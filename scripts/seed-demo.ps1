param(
    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"

function Invoke-ApiJson {
    param(
        [Parameter(Mandatory = $true)][ValidateSet("GET", "POST", "PUT", "PATCH")] [string]$Method,
        [Parameter(Mandatory = $true)] [string]$Url,
        [hashtable]$Body,
        [string]$Token
    )

    $headers = @{}
    if ($Token) {
        $headers["Authorization"] = "Bearer $Token"
    }

    try {
        if ($Body) {
            return @{
                ok = $true
                status = 200
                data = Invoke-RestMethod -Method $Method -Uri $Url -Headers $headers -ContentType "application/json" -Body ($Body | ConvertTo-Json -Depth 20)
            }
        }

        return @{
            ok = $true
            status = 200
            data = Invoke-RestMethod -Method $Method -Uri $Url -Headers $headers
        }
    } catch {
        $status = 0
        if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
            $status = [int]$_.Exception.Response.StatusCode
        }
        return @{
            ok = $false
            status = $status
            error = $_.ErrorDetails.Message
        }
    }
}

function Ensure-Account {
    param(
        [string]$Email,
        [string]$Password,
        [string]$Role
    )

    $loginResult = Invoke-ApiJson -Method "POST" -Url "$BaseUrl/auth/login" -Body @{
        email = $Email
        password = $Password
    }
    if ($loginResult.ok) {
        return $loginResult.data
    }

    if ($loginResult.status -ne 401) {
        throw "Failed login for $Email. Status: $($loginResult.status). Error: $($loginResult.error)"
    }

    $registerResult = Invoke-ApiJson -Method "POST" -Url "$BaseUrl/auth/register" -Body @{
        email = $Email
        password = $Password
        role = $Role
    }
    if (-not $registerResult.ok) {
        throw "Failed register for $Email. Status: $($registerResult.status). Error: $($registerResult.error)"
    }
    return $registerResult.data
}

function Ensure-Profile {
    param(
        [string]$Token,
        [hashtable]$Payload
    )

    $result = Invoke-ApiJson -Method "PUT" -Url "$BaseUrl/users/me" -Body $Payload -Token $Token
    if (-not $result.ok) {
        throw "Failed upsert profile. Status: $($result.status). Error: $($result.error)"
    }
}

Write-Host "Seeding demo data via gateway: $BaseUrl"

$clientAuth = Ensure-Account -Email "client.demo@skillbridge.local" -Password "Demo12345!" -Role "CLIENT"
$freelancerAuth = Ensure-Account -Email "freelancer.demo@skillbridge.local" -Password "Demo12345!" -Role "FREELANCER"

Ensure-Profile -Token $clientAuth.accessToken -Payload @{
    companyName = "SkillBridge Demo Co."
}
Ensure-Profile -Token $freelancerAuth.accessToken -Payload @{
    skills = @("java", "spring", "postgresql")
    hourlyRate = 45
    overview = "Demo freelancer account for system walkthrough."
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$createJob = Invoke-ApiJson -Method "POST" -Url "$BaseUrl/jobs" -Token $clientAuth.accessToken -Body @{
    title = "Demo Backend Job $timestamp"
    description = "Implement REST endpoint hardening and event notifications."
    budgetMin = 300
    budgetMax = 600
    tags = @("java", "spring-boot", "rabbitmq")
}
if (-not $createJob.ok) {
    throw "Failed create job. Status: $($createJob.status). Error: $($createJob.error)"
}

$jobId = [int64]$createJob.data.id

$applyProposal = Invoke-ApiJson -Method "POST" -Url "$BaseUrl/proposals" -Token $freelancerAuth.accessToken -Body @{
    jobId = $jobId
    coverLetter = "I can deliver this in 7 days with tests and docs."
    price = 450
    durationDays = 7
}
if (-not $applyProposal.ok) {
    throw "Failed apply proposal. Status: $($applyProposal.status). Error: $($applyProposal.error)"
}

$proposalId = [int64]$applyProposal.data.id

$acceptProposal = Invoke-ApiJson -Method "PATCH" -Url "$BaseUrl/proposals/$proposalId/accept" -Token $clientAuth.accessToken
if (-not $acceptProposal.ok) {
    throw "Failed accept proposal. Status: $($acceptProposal.status). Error: $($acceptProposal.error)"
}

$contracts = Invoke-ApiJson -Method "GET" -Url "$BaseUrl/contracts/me" -Token $freelancerAuth.accessToken
$notifications = Invoke-ApiJson -Method "GET" -Url "$BaseUrl/notifications/me" -Token $freelancerAuth.accessToken

if (-not $contracts.ok) {
    throw "Failed fetch contracts. Status: $($contracts.status). Error: $($contracts.error)"
}
if (-not $notifications.ok) {
    throw "Failed fetch notifications. Status: $($notifications.status). Error: $($notifications.error)"
}

Write-Host ""
Write-Host "Demo users:"
Write-Host "  CLIENT      : client.demo@skillbridge.local / Demo12345!"
Write-Host "  FREELANCER  : freelancer.demo@skillbridge.local / Demo12345!"
Write-Host ""
Write-Host "Created entities:"
Write-Host "  Job ID      : $jobId"
Write-Host "  Proposal ID : $proposalId"
Write-Host "  Contracts   : $($contracts.data.Count)"
Write-Host "  Notifications (freelancer): $($notifications.data.Count)"
Write-Host ""
Write-Host "Seed completed."
