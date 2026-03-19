package com.skillbridge.proposal_service.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.skillbridge.proposal_service.config.FileStorageProperties;
import com.skillbridge.proposal_service.domain.Proposal;
import com.skillbridge.proposal_service.domain.ProposalCvFile;
import com.skillbridge.proposal_service.dto.ProposalCvFileResponse;
import com.skillbridge.proposal_service.repository.ProposalCvFileRepository;
import com.skillbridge.proposal_service.repository.ProposalRepository;
import com.skillbridge.proposal_service.security.JwtUserPrincipal;

@Service
public class ProposalCvService {

    private static final Logger log = LoggerFactory.getLogger(ProposalCvService.class);
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".pdf", ".doc", ".docx");

    private final ProposalRepository proposalRepository;
    private final ProposalCvFileRepository proposalCvFileRepository;
    private final FileStorageService fileStorageService;
    private final FileStorageProperties fileStorageProperties;
    private final RestClient jobRestClient;

    public ProposalCvService(
            ProposalRepository proposalRepository,
            ProposalCvFileRepository proposalCvFileRepository,
            FileStorageService fileStorageService,
            FileStorageProperties fileStorageProperties,
            @Value("${app.services.job-base-url:http://localhost:8083}") String jobBaseUrl
    ) {
        this.proposalRepository = proposalRepository;
        this.proposalCvFileRepository = proposalCvFileRepository;
        this.fileStorageService = fileStorageService;
        this.fileStorageProperties = fileStorageProperties;
        this.jobRestClient = RestClient.builder().baseUrl(jobBaseUrl).build();
    }

    @Transactional
    public ProposalCvFileResponse uploadCv(Long proposalId, MultipartFile file, JwtUserPrincipal principal) {
        ensureStorageEnabled();
        ensureRole(principal, "FREELANCER", "Only FREELANCER can upload CV");
        Proposal proposal = findProposal(proposalId);
        assertProposalFreelancer(proposal, principal.userId());

        ValidatedFile validatedFile = validateFile(file);
        String objectKey = buildObjectKey(proposalId, validatedFile.fileName());
        ProposalCvFile existing = proposalCvFileRepository.findByProposalId(proposalId).orElse(null);
        FileStorageService.FileReference previousReference = existing == null ? null : toReference(existing);

        FileStorageService.StoredFile storedFile;
        try {
            storedFile = fileStorageService.store(new FileStorageService.StoreFileRequest(
                    objectKey,
                    validatedFile.fileName(),
                    validatedFile.contentType(),
                    validatedFile.content()
            ));
        } catch (FileStorageException ex) {
            log.warn("Failed to upload CV for proposalId={} ownerUserId={}: {}", proposalId, principal.userId(), ex.getMessage(), ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "CV storage provider is unavailable");
        }

        ProposalCvFile metadata = existing == null ? new ProposalCvFile() : existing;
        metadata.setProposalId(proposalId);
        metadata.setOwnerUserId(proposal.getFreelancerId());
        metadata.setObjectKey(storedFile.objectKey());
        metadata.setOriginalFileName(validatedFile.fileName());
        metadata.setContentType(storedFile.contentType());
        metadata.setSizeBytes(storedFile.sizeBytes());
        metadata.setStorageProvider(storedFile.provider());
        metadata.setBucketName(storedFile.bucketName());
        metadata.setUploadedAt(Instant.now());

        try {
            ProposalCvFile saved = proposalCvFileRepository.save(metadata);
            deleteOldFileIfReplaced(previousReference, saved);
            return toResponse(saved);
        } catch (RuntimeException ex) {
            log.warn("Failed to persist CV metadata for proposalId={}: {}", proposalId, ex.getMessage(), ex);
            safeDeleteStoredObject(storedFile, validatedFile.fileName());
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public ProposalCvFileResponse getCvMetadata(Long proposalId, JwtUserPrincipal principal) {
        ensureStorageEnabled();
        Proposal proposal = findProposal(proposalId);
        assertCvAccess(proposal, principal);
        ProposalCvFile metadata = proposalCvFileRepository.findByProposalId(proposalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "CV file not found"));
        return toResponse(metadata);
    }

    @Transactional(readOnly = true)
    public DownloadedCvFile downloadCv(Long proposalId, JwtUserPrincipal principal) {
        ensureStorageEnabled();
        Proposal proposal = findProposal(proposalId);
        assertCvAccess(proposal, principal);
        ProposalCvFile metadata = proposalCvFileRepository.findByProposalId(proposalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "CV file not found"));

        try {
            FileStorageService.StoredFileContent storedFile = fileStorageService.load(toReference(metadata));
            return new DownloadedCvFile(
                    storedFile.originalFileName(),
                    storedFile.contentType(),
                    storedFile.content()
            );
        } catch (FileStorageException ex) {
            log.warn("Failed to load CV content for proposalId={}: {}", proposalId, ex.getMessage(), ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "CV file could not be loaded");
        }
    }

    private void ensureStorageEnabled() {
        if (!fileStorageProperties.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "CV file storage is disabled");
        }
    }

    private Proposal findProposal(Long proposalId) {
        return proposalRepository.findById(proposalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proposal not found"));
    }

    private void assertProposalFreelancer(Proposal proposal, Long freelancerId) {
        if (proposal.getFreelancerId() == null || !proposal.getFreelancerId().equals(freelancerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only proposal owner can manage CV");
        }
    }

    private void assertCvAccess(Proposal proposal, JwtUserPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        if (proposal.getFreelancerId() != null && proposal.getFreelancerId().equals(principal.userId())) {
            return;
        }
        Long clientId = proposal.getClientId() != null ? proposal.getClientId() : fetchJobOwnerId(proposal.getJobId());
        if (clientId != null && clientId.equals(principal.userId())) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to access this CV");
    }

    private void ensureRole(JwtUserPrincipal principal, String expectedRole, String message) {
        if (principal == null || principal.role() == null || !expectedRole.equalsIgnoreCase(principal.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, message);
        }
    }

    private Long fetchJobOwnerId(Long jobId) {
        try {
            JobSummary job = jobRestClient.get()
                    .uri("/jobs/{id}", jobId)
                    .retrieve()
                    .body(JobSummary.class);
            if (job == null || job.clientId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid response from job-service");
            }
            return job.clientId();
        } catch (HttpClientErrorException.NotFound ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found");
        } catch (HttpClientErrorException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to verify job owner");
        } catch (ResourceAccessException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "job-service is unavailable");
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to communicate with job-service");
        }
    }

    private ValidatedFile validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CV file is required");
        }
        if (file.getSize() > fileStorageProperties.maxCvFileSizeBytes()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "CV file must be " + fileStorageProperties.getMaxCvFileSizeMb() + "MB or smaller"
            );
        }

        String fileName = normalizeFileName(file.getOriginalFilename());
        if (fileName == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CV file name is invalid");
        }

        String contentType = normalizeContentType(file.getContentType());
        if (!isAllowedContentType(contentType, fileName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CV must be a PDF or Word document");
        }

        try {
            return new ValidatedFile(fileName, contentType, file.getBytes());
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not process CV file");
        }
    }

    private String normalizeFileName(String originalFileName) {
        if (originalFileName == null) {
            return null;
        }
        String normalized = originalFileName.trim().replace('\\', '/');
        String fileName = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (fileName.isEmpty()) {
            return null;
        }
        return fileName.replaceAll("[\\r\\n\"]", "_");
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "application/octet-stream";
        }
        return contentType.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isAllowedContentType(String contentType, String fileName) {
        Set<String> allowedTypes = fileStorageProperties.getAllowedCvContentTypes().stream()
                .map(value -> value == null ? null : value.trim().toLowerCase(Locale.ROOT))
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.toSet());
        if (allowedTypes.contains(contentType)) {
            return true;
        }
        return "application/octet-stream".equals(contentType) && hasAllowedExtension(fileName);
    }

    private boolean hasAllowedExtension(String fileName) {
        String normalized = fileName.toLowerCase(Locale.ROOT);
        return ALLOWED_EXTENSIONS.stream().anyMatch(normalized::endsWith);
    }

    private String buildObjectKey(Long proposalId, String fileName) {
        String normalizedName = fileName.replaceAll("[^A-Za-z0-9._-]", "_");
        return "proposals/%d/cv/%s-%s".formatted(proposalId, UUID.randomUUID(), normalizedName);
    }

    private void deleteOldFileIfReplaced(FileStorageService.FileReference previousReference, ProposalCvFile current) {
        if (previousReference == null || previousReference.objectKey() == null) {
            return;
        }
        if (previousReference.objectKey().equals(current.getObjectKey())
                && previousReference.provider() == current.getStorageProvider()) {
            return;
        }
        try {
            fileStorageService.delete(previousReference);
        } catch (FileStorageException ex) {
            log.warn("Failed to delete replaced CV object for proposalId={}: {}", current.getProposalId(), ex.getMessage(), ex);
        }
    }

    private void safeDeleteStoredObject(FileStorageService.StoredFile storedFile, String originalFileName) {
        try {
            fileStorageService.delete(new FileStorageService.FileReference(
                    storedFile.provider(),
                    storedFile.bucketName(),
                    storedFile.objectKey(),
                    originalFileName,
                    storedFile.contentType()
            ));
        } catch (FileStorageException deleteEx) {
            log.warn("Failed to clean up stored CV object key={}: {}", storedFile.objectKey(), deleteEx.getMessage(), deleteEx);
        }
    }

    private FileStorageService.FileReference toReference(ProposalCvFile metadata) {
        return new FileStorageService.FileReference(
                metadata.getStorageProvider(),
                metadata.getBucketName(),
                metadata.getObjectKey(),
                metadata.getOriginalFileName(),
                metadata.getContentType()
        );
    }

    private ProposalCvFileResponse toResponse(ProposalCvFile metadata) {
        return new ProposalCvFileResponse(
                metadata.getId(),
                metadata.getProposalId(),
                metadata.getOwnerUserId(),
                metadata.getOriginalFileName(),
                metadata.getObjectKey(),
                metadata.getContentType(),
                metadata.getSizeBytes(),
                metadata.getStorageProvider().code(),
                metadata.getBucketName(),
                metadata.getUploadedAt(),
                "/proposals/%d/cv/download".formatted(metadata.getProposalId())
        );
    }

    private record ValidatedFile(
            String fileName,
            String contentType,
            byte[] content
    ) {
    }

    private record JobSummary(
            Long id,
            Long clientId,
            String title,
            BigDecimal budgetMin,
            BigDecimal budgetMax
    ) {
    }

    public record DownloadedCvFile(
            String fileName,
            String contentType,
            byte[] content
    ) {
    }
}
