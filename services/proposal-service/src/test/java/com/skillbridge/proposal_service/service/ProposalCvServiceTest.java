package com.skillbridge.proposal_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.skillbridge.proposal_service.config.FileStorageProperties;
import com.skillbridge.proposal_service.domain.FileStorageProvider;
import com.skillbridge.proposal_service.domain.Proposal;
import com.skillbridge.proposal_service.domain.ProposalCvFile;
import com.skillbridge.proposal_service.dto.ProposalCvFileResponse;
import com.skillbridge.proposal_service.repository.ProposalCvFileRepository;
import com.skillbridge.proposal_service.repository.ProposalRepository;
import com.skillbridge.proposal_service.security.JwtUserPrincipal;

@ExtendWith(MockitoExtension.class)
class ProposalCvServiceTest {

    @Mock
    private ProposalRepository proposalRepository;

    @Mock
    private ProposalCvFileRepository proposalCvFileRepository;

    @TempDir
    Path tempDir;

    @Test
    void uploadCvShouldPersistMetadataForProposalOwner() {
        MockFileStorageService storageService = mockStorage(true);
        ProposalCvService proposalCvService = createService(true, storageService);
        Proposal proposal = proposal(55L, 7L, 10L);

        when(proposalRepository.findById(55L)).thenReturn(Optional.of(proposal));
        when(proposalCvFileRepository.findByProposalId(55L)).thenReturn(Optional.empty());
        when(proposalCvFileRepository.save(any(ProposalCvFile.class))).thenAnswer(invocation -> {
            ProposalCvFile metadata = invocation.getArgument(0);
            metadata.setId(100L);
            return metadata;
        });

        ProposalCvFileResponse response = proposalCvService.uploadCv(
                55L,
                new MockMultipartFile(
                        "file",
                        "candidate-cv.pdf",
                        "application/pdf",
                        "cv-content".getBytes(StandardCharsets.UTF_8)
                ),
                new JwtUserPrincipal(7L, "candidate@example.com", "FREELANCER")
        );

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.proposalId()).isEqualTo(55L);
        assertThat(response.originalFileName()).isEqualTo("candidate-cv.pdf");
        assertThat(response.storageProvider()).isEqualTo("mock");
        assertThat(response.downloadUrl()).isEqualTo("/proposals/55/cv/download");
        assertThat(Files.exists(tempDir.resolve(response.objectKey()))).isTrue();
    }

    @Test
    void uploadCvShouldReplaceExistingCvAndDeleteOldObject() throws Exception {
        MockFileStorageService storageService = mockStorage(true);
        ProposalCvService proposalCvService = createService(true, storageService);
        Proposal proposal = proposal(77L, 7L, 10L);

        String oldObjectKey = "proposals/77/cv/old-cv.pdf";
        storageService.store(new FileStorageService.StoreFileRequest(
                oldObjectKey,
                "old-cv.pdf",
                "application/pdf",
                "old-content".getBytes(StandardCharsets.UTF_8)
        ));

        ProposalCvFile existing = new ProposalCvFile();
        existing.setId(5L);
        existing.setProposalId(77L);
        existing.setOwnerUserId(7L);
        existing.setObjectKey(oldObjectKey);
        existing.setOriginalFileName("old-cv.pdf");
        existing.setContentType("application/pdf");
        existing.setSizeBytes(11);
        existing.setStorageProvider(FileStorageProvider.MOCK);
        existing.setBucketName("mock-local");

        when(proposalRepository.findById(77L)).thenReturn(Optional.of(proposal));
        when(proposalCvFileRepository.findByProposalId(77L)).thenReturn(Optional.of(existing));
        when(proposalCvFileRepository.save(any(ProposalCvFile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProposalCvFileResponse response = proposalCvService.uploadCv(
                77L,
                new MockMultipartFile(
                        "file",
                        "new-cv.docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "new-content".getBytes(StandardCharsets.UTF_8)
                ),
                new JwtUserPrincipal(7L, "candidate@example.com", "FREELANCER")
        );

        assertThat(response.objectKey()).isNotEqualTo(oldObjectKey);
        assertThat(Files.exists(tempDir.resolve(response.objectKey()))).isTrue();
        assertThat(Files.exists(tempDir.resolve(oldObjectKey))).isFalse();
    }

    @Test
    void uploadCvShouldRejectUnsupportedFileType() {
        MockFileStorageService storageService = mockStorage(true);
        ProposalCvService proposalCvService = createService(true, storageService);
        Proposal proposal = proposal(88L, 7L, 10L);

        when(proposalRepository.findById(88L)).thenReturn(Optional.of(proposal));

        assertThatThrownBy(() -> proposalCvService.uploadCv(
                88L,
                new MockMultipartFile(
                        "file",
                        "avatar.png",
                        "image/png",
                        "png-content".getBytes(StandardCharsets.UTF_8)
                ),
                new JwtUserPrincipal(7L, "candidate@example.com", "FREELANCER")
        ))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        verify(proposalCvFileRepository, never()).save(any(ProposalCvFile.class));
    }

    @Test
    void recruiterOwnerShouldViewAndDownloadProposalCv() {
        MockFileStorageService storageService = mockStorage(true);
        ProposalCvService proposalCvService = createService(true, storageService);
        Proposal proposal = proposal(99L, 7L, 10L);
        String objectKey = "proposals/99/cv/recruiter-view.pdf";
        storageService.store(new FileStorageService.StoreFileRequest(
                objectKey,
                "recruiter-view.pdf",
                "application/pdf",
                "downloadable-content".getBytes(StandardCharsets.UTF_8)
        ));

        ProposalCvFile metadata = new ProposalCvFile();
        metadata.setId(8L);
        metadata.setProposalId(99L);
        metadata.setOwnerUserId(7L);
        metadata.setObjectKey(objectKey);
        metadata.setOriginalFileName("recruiter-view.pdf");
        metadata.setContentType("application/pdf");
        metadata.setSizeBytes(20);
        metadata.setStorageProvider(FileStorageProvider.MOCK);
        metadata.setBucketName("mock-local");

        when(proposalRepository.findById(99L)).thenReturn(Optional.of(proposal));
        when(proposalCvFileRepository.findByProposalId(99L)).thenReturn(Optional.of(metadata));

        JwtUserPrincipal recruiter = new JwtUserPrincipal(10L, "recruiter@example.com", "CLIENT");
        ProposalCvFileResponse response = proposalCvService.getCvMetadata(99L, recruiter);
        ProposalCvService.DownloadedCvFile downloadedCv = proposalCvService.downloadCv(99L, recruiter);

        assertThat(response.originalFileName()).isEqualTo("recruiter-view.pdf");
        assertThat(downloadedCv.fileName()).isEqualTo("recruiter-view.pdf");
        assertThat(downloadedCv.contentType()).isEqualTo("application/pdf");
        assertThat(new String(downloadedCv.content(), StandardCharsets.UTF_8)).isEqualTo("downloadable-content");
    }

    @Test
    void uploadCvShouldRejectWhenStorageIsDisabled() {
        MockFileStorageService storageService = mockStorage(false);
        ProposalCvService proposalCvService = createService(false, storageService);

        assertThatThrownBy(() -> proposalCvService.uploadCv(
                55L,
                new MockMultipartFile(
                        "file",
                        "candidate-cv.pdf",
                        "application/pdf",
                        "cv-content".getBytes(StandardCharsets.UTF_8)
                ),
                new JwtUserPrincipal(7L, "candidate@example.com", "FREELANCER")
        ))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    private ProposalCvService createService(boolean enabled, FileStorageService storageService) {
        FileStorageProperties properties = new FileStorageProperties();
        properties.setEnabled(enabled);
        properties.setProvider("mock");
        properties.setMaxCvFileSizeMb(10);
        properties.getMock().setBaseDir(tempDir.toString());
        properties.getMock().setBucket("mock-local");
        return new ProposalCvService(
                proposalRepository,
                proposalCvFileRepository,
                storageService,
                properties,
                "http://localhost:65535"
        );
    }

    private MockFileStorageService mockStorage(boolean enabled) {
        FileStorageProperties properties = new FileStorageProperties();
        properties.setEnabled(enabled);
        properties.setProvider("mock");
        properties.getMock().setBaseDir(tempDir.toString());
        properties.getMock().setBucket("mock-local");
        return new MockFileStorageService(properties);
    }

    private Proposal proposal(Long proposalId, Long freelancerId, Long clientId) {
        Proposal proposal = new Proposal();
        proposal.setId(proposalId);
        proposal.setJobId(1000L + proposalId);
        proposal.setFreelancerId(freelancerId);
        proposal.setClientId(clientId);
        return proposal;
    }
}
