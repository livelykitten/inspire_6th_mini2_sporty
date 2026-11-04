#Requires -Version 7.0
[CmdletBinding()]
param(
    [Alias('BASE_URL')]
    [string]$BaseUrl = $(if ($env:BASE_URL) { $env:BASE_URL } else { 'http://localhost:8000' }),
    [Parameter(Mandatory)]
    [System.Collections.IDictionary]$Accounts
)

$ErrorActionPreference = 'Stop'
$baseUri = $null
if (-not [Uri]::TryCreate($BaseUrl, [UriKind]::Absolute, [ref]$baseUri) -or
    $baseUri.Scheme -notin @('http', 'https') -or $baseUri.UserInfo -or
    $baseUri.Query -or $baseUri.Fragment) {
    throw 'BASE_URL must be an HTTP(S) Gateway URL without credentials, query, or fragment.'
}
$BaseUrl = $BaseUrl.TrimEnd('/')

$requiredRoles = @('owner', 'participant', 'withdrawal')
foreach ($role in $requiredRoles) {
    if (-not $Accounts.Contains($role)) { throw "Missing demo account role: $role" }
    $account = $Accounts[$role]
    if (-not $account.UserId -or [long]$account.UserId -le 0 -or
        [string]::IsNullOrWhiteSpace([string]$account.AccessToken)) {
        throw "Invalid demo account session for role: $role"
    }
}

function Invoke-DemoApi([string]$method, [string]$path, $body, [string]$token) {
    $options = @{
        Uri = "$BaseUrl$path"
        Method = $method
        TimeoutSec = 20
        SkipHttpErrorCheck = $true
    }
    if ($null -ne $body) {
        $options.ContentType = 'application/json; charset=utf-8'
        $options.Body = [Text.Encoding]::UTF8.GetBytes(
            (ConvertTo-Json $body -Depth 5 -Compress)
        )
    }
    if ($token) { $options.Headers = @{ Authorization = "Bearer $token" } }
    try { $response = Invoke-WebRequest @options }
    catch { throw "Gateway request failed: $method $path. Check BASE_URL and service health." }

    $data = $null
    if ($response.Content) {
        try { $data = ConvertFrom-Json $response.Content }
        catch { throw "Gateway returned non-JSON content: $method $path HTTP $([int]$response.StatusCode)." }
    }
    return @{ Status = [int]$response.StatusCode; Data = $data }
}

function Get-MyMatches($account) {
    $response = Invoke-DemoApi GET '/api/matches/me' $null $account.AccessToken
    if ($response.Status -ne 200) {
        throw "My matches lookup failed for role $($account.Role): HTTP $($response.Status)."
    }
    return @($response.Data)
}

function Find-OwnedDemoMatch($account, [string]$title) {
    $matches = @(Get-MyMatches $account | Where-Object {
        $_.title -ceq $title -and $_.role -eq 'OWNER'
    })
    if ($matches.Count -gt 1) {
        throw "Duplicate demo matches already exist for title: $title"
    }
    return $(if ($matches.Count -eq 1) { $matches[0] } else { $null })
}

function New-MatchBody([long]$serviceId, [string]$title, [string]$description, [int]$capacity) {
    $start = (Get-Date).Date.AddDays(2).AddHours(19)
    return @{
        serviceId = $serviceId
        title = $title
        description = $description
        startAt = $start.ToString('yyyy-MM-ddTHH:mm:ss')
        endAt = $start.AddHours(2).ToString('yyyy-MM-ddTHH:mm:ss')
        maxParticipant = $capacity
        skillLevel = 'BEGINNER'
        sportType = 'FUTSAL'
        genderGroup = 'MIXED'
    }
}

function Get-FacilityCandidates {
    $response = Invoke-DemoApi GET '/api/services' $null $null
    if ($response.Status -ne 200) {
        throw "Facility lookup failed: HTTP $($response.Status)."
    }
    $ids = @($response.Data | ForEach-Object { [long]$_.serviceId } |
        Where-Object { $_ -gt 0 })

    # A restarted local facility DB may be empty while match-service still has valid
    # event snapshots. Reuse only IDs exposed by existing public match details;
    # match creation below remains the final active-snapshot check.
    if ($ids.Count -eq 0) {
        $matches = Invoke-DemoApi GET '/api/matches' $null $null
        if ($matches.Status -ne 200) {
            throw "Match lookup for facility candidates failed: HTTP $($matches.Status)."
        }
        foreach ($match in @($matches.Data)) {
            if (-not $match.matchId) { continue }
            $detail = Invoke-DemoApi GET "/api/matches/$($match.matchId)" $null $null
            if ($detail.Status -eq 200 -and $detail.Data.serviceId) {
                $ids += [long]$detail.Data.serviceId
            }
        }
    }
    $ids = @($ids | Sort-Object -Unique)
    if ($ids.Count -eq 0) {
        throw 'No facility candidates are available. Run facility synchronization first.'
    }
    return $ids
}

function Create-FirstMatchOnActiveSnapshot($account, [string]$title, [string]$description, [int]$capacity) {
    $existing = Find-OwnedDemoMatch $account $title
    if ($existing) {
        $detail = Invoke-DemoApi GET "/api/matches/$($existing.matchId)" $null $account.AccessToken
        if ($detail.Status -ne 200 -or -not $detail.Data.serviceId) {
            throw "Existing demo match could not be verified: $title"
        }
        Write-Host ("owner match: {0} matchId={1} serviceId={2} REUSED" -f
            $title, $existing.matchId, $detail.Data.serviceId)
        return @{ MatchId = [long]$existing.matchId; ServiceId = [long]$detail.Data.serviceId }
    }

    foreach ($serviceId in Get-FacilityCandidates) {
        $response = Invoke-DemoApi POST '/api/matches' `
            (New-MatchBody $serviceId $title $description $capacity) $account.AccessToken
        if ($response.Status -eq 201) {
            Write-Host ("owner match: {0} matchId={1} serviceId={2} CREATED" -f
                $title, $response.Data, $serviceId)
            return @{ MatchId = [long]$response.Data; ServiceId = [long]$serviceId }
        }
        if ($response.Status -eq 404 -and $response.Data.code -eq 'SERVICE_NOT_FOUND') {
            continue
        }
        throw "Match creation failed for facility ${serviceId}: HTTP $($response.Status) $($response.Data.code)"
    }
    throw 'No active facility snapshot accepted match creation. Wait for EV-01 synchronization and retry.'
}

function Ensure-OwnedMatch($account, [long]$serviceId, [string]$title, [string]$description, [int]$capacity) {
    $existing = Find-OwnedDemoMatch $account $title
    if ($existing) {
        Write-Host ("owner match: {0} matchId={1} REUSED" -f $title, $existing.matchId)
        return [long]$existing.matchId
    }
    $response = Invoke-DemoApi POST '/api/matches' `
        (New-MatchBody $serviceId $title $description $capacity) $account.AccessToken
    if ($response.Status -ne 201) {
        throw "Match creation failed for title ${title}: HTTP $($response.Status) $($response.Data.code)"
    }
    Write-Host ("owner match: {0} matchId={1} CREATED" -f $title, $response.Data)
    return [long]$response.Data
}

function Ensure-Participant($account, [long]$matchId) {
    $joined = @(Get-MyMatches $account | Where-Object {
        [long]$_.matchId -eq $matchId -and $_.role -eq 'PARTICIPANT'
    })
    if ($joined.Count -eq 1) {
        Write-Host ("participant: role={0} matchId={1} REUSED" -f $account.Role, $matchId)
        return
    }
    if ($joined.Count -gt 1) { throw "Duplicate participant rows detected for matchId=$matchId" }

    $response = Invoke-DemoApi POST "/api/matches/$matchId/participants" $null $account.AccessToken
    if ($response.Status -ne 201) {
        throw "Match join failed for role $($account.Role): HTTP $($response.Status) $($response.Data.code)"
    }
    Write-Host ("participant: role={0} matchId={1} CREATED" -f $account.Role, $matchId)
}

$owner = $Accounts['owner']
$participant = $Accounts['participant']
$withdrawal = $Accounts['withdrawal']
$mainTitle = '[DEMO] 함께하는 풋살 매치'
$withdrawalTitle = "[DEMO] 탈퇴 검증 소유 매치 - $($withdrawal.Email)"
if ($withdrawalTitle.Length -gt 50) {
    $withdrawalTitle = "[DEMO] 탈퇴 검증 소유 매치 - user-$($withdrawal.UserId)"
}

$main = Create-FirstMatchOnActiveSnapshot $owner $mainTitle `
    '시연용 공개 매치입니다. 참가와 회원 탈퇴 Saga 흐름을 확인합니다.' 4
Ensure-Participant $participant $main.MatchId
Ensure-Participant $withdrawal $main.MatchId
$withdrawalOwnedMatchId = Ensure-OwnedMatch $withdrawal $main.ServiceId $withdrawalTitle `
    '탈퇴 시 소유 매치 삭제를 확인하기 위한 시연 데이터입니다.' 3

$mainDetail = Invoke-DemoApi GET "/api/matches/$($main.MatchId)" $null $owner.AccessToken
if ($mainDetail.Status -ne 200 -or $mainDetail.Data.currentParticipantCount -lt 3 -or
    $mainDetail.Data.currentParticipantCount -gt $mainDetail.Data.maxParticipant) {
    throw 'Main demo match verification failed; required participants are missing or capacity was exceeded.'
}
$withdrawalDetail = Invoke-DemoApi GET "/api/matches/$withdrawalOwnedMatchId" $null $withdrawal.AccessToken
if ($withdrawalDetail.Status -ne 200 -or -not $withdrawalDetail.Data.isOwner) {
    throw 'Withdrawal-owned demo match verification failed.'
}

Write-Host ("Demo matches ready. mainMatchId={0} withdrawalOwnedMatchId={1} serviceId={2}" -f
    $main.MatchId, $withdrawalOwnedMatchId, $main.ServiceId)
Write-Host 'Access tokens and passwords were not printed or saved. Existing data was not deleted or reset.'
