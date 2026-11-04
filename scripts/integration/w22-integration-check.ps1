#Requires -Version 7.0
param(
    [string]$ApiUrl = 'http://localhost:8000',
    [string]$UserUrl = 'http://localhost:8001',
    [int]$TimeoutSeconds = 60
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Set-Location $repoRoot
$nonce = [guid]::NewGuid().ToString('N').Substring(0, 12)
$password = 'W22integration123!'

function Check($condition, [string]$message) {
    if (-not $condition) { throw $message }
    Write-Host "PASS: $message"
}

function Request([string]$method, [string]$path, $body, $token) {
    $options = @{
        Uri = "$ApiUrl$path"
        Method = $method
        SkipHttpErrorCheck = $true
        TimeoutSec = 15
    }
    if ($null -ne $body) {
        $options.ContentType = 'application/json'
        $options.Body = ConvertTo-Json $body -Depth 10 -Compress
    }
    if ($token) { $options.Headers = @{ Authorization = "Bearer $token" } }
    $response = Invoke-WebRequest @options
    $data = if ($response.Content) { ConvertFrom-Json $response.Content } else { $null }
    return @{ Status = [int]$response.StatusCode; Data = $data }
}

function Sql([string]$database, [string]$query) {
    $result = $query | & docker compose exec -T $database sh -c 'exec mariadb -u"$MARIADB_USER" -p"$MARIADB_PASSWORD" "$MARIADB_DATABASE" --batch --skip-column-names' 2>&1
    if ($LASTEXITCODE -ne 0) { throw "DB query failed: $result" }
    return (($result | ForEach-Object { "$_" }) -join "`n").Trim()
}

function RedisExists([long]$memberId) {
    $result = & docker compose exec -T redis redis-cli EXISTS "refresh:$memberId"
    if ($LASTEXITCODE -ne 0) { throw 'Redis query failed' }
    return [int]("$result".Trim())
}

function NewMember([string]$label) {
    $email = "w22-$nonce-$label@example.com"
    $signup = Request POST '/api/users' @{
        email = $email; password = $password; gender = 'MALE'
        nickname = "w22_${nonce}_$label"; district = 'GANGNAM'; sportTypes = @()
    } $null
    Check ($signup.Status -eq 201) "$label signup returns 201"
    $login = Request POST '/api/auth/login' @{ email = $email; password = $password } $null
    Check ($login.Status -eq 200) "$label login returns 200"
    return @{ Id = [long]$login.Data.userId; Token = $login.Data.accessToken; Refresh = $login.Data.refreshToken; Email = $email }
}

function CreateMatch($member, [string]$label, [int]$capacity) {
    $response = Request POST '/api/matches' @{
        serviceId = $fixtureId; title = "w22-$nonce-$label"; description = 'W22 integration fixture'
        startAt = (Get-Date).AddDays(2).ToString('yyyy-MM-ddTHH:mm:ss')
        endAt = (Get-Date).AddDays(2).AddHours(1).ToString('yyyy-MM-ddTHH:mm:ss')
        maxParticipant = $capacity; skillLevel = 'BEGINNER'; sportType = 'SOCCER'; genderGroup = 'MIXED'
    } $member.Token
    Check ($response.Status -eq 201) "$label match creation returns 201"
    return [long]$response.Data
}

Write-Host "Run: $nonce (tokens and passwords are not printed)"
$withdrawing = NewMember 'withdraw'
$remaining = NewMember 'remain'
$concurrent = NewMember 'concurrent'
$fixtureId = [long]('9' + (Get-Date -Format 'yyMMddHHmmss'))
$null = Sql 'match-db' "INSERT INTO facility_service_snapshot (service_id,name,location_name,region,is_free,active,source_updated_at,updated_at) VALUES ($fixtureId,'w22-$nonce','integration','GANGNAM',1,1,NOW(),NOW());"

$ownedMatch = CreateMatch $withdrawing 'owned' 3
$remainingMatch = CreateMatch $remaining 'remaining' 2
$join = Request POST "/api/matches/$remainingMatch/participants" $null $withdrawing.Token
Check ($join.Status -eq 201) 'withdrawing member joins another owner match'
$before = Request GET "/api/matches/$remainingMatch" $null $remaining.Token
Check ($before.Data.status -eq 'CLOSED' -and $before.Data.currentParticipantCount -eq 2) 'full match is CLOSED with two participants'
Check ((RedisExists $withdrawing.Id) -eq 1) 'refresh token exists before withdrawal'

$accepted = Request DELETE '/api/users/me' @{ password = $password } $withdrawing.Token
Check ($accepted.Status -eq 202) 'withdrawal acceptance returns 202 through gateway'
$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
do {
    $state = Invoke-RestMethod "$UserUrl/internal/users/$($withdrawing.Id)/status"
    if ($state.status -eq 'WITHDRAWN') { break }
    Start-Sleep -Milliseconds 500
} while ((Get-Date) -lt $deadline)
Check ($state.status -eq 'WITHDRAWN' -and $null -eq $state.sagaId) 'real EV-02 / EV-03 flow confirms WITHDRAWN and clears sagaId'
do {
    $tokenExists = RedisExists $withdrawing.Id
    if ($tokenExists -eq 0) { break }
    Start-Sleep -Milliseconds 200
} while ((Get-Date) -lt $deadline)
Check ($tokenExists -eq 0) 'refresh token is deleted after completion'
Check ((Request GET "/api/matches/$ownedMatch" $null $null).Status -eq 404) 'owned match is deleted'
$after = Request GET "/api/matches/$remainingMatch" $null $remaining.Token
Check ($after.Status -eq 200 -and $after.Data.status -eq 'RECRUITING' -and $after.Data.currentParticipantCount -eq 1) 'other owner match remains and reopens with one participant'
Check ((Sql 'match-db' "SELECT COUNT(*) FROM match_participant WHERE user_id=$($withdrawing.Id);") -eq '0') 'all withdrawing member participation rows are removed'
Check ((Sql 'match-db' "SELECT is_blocked+0 FROM match_user_guard WHERE user_id=$($withdrawing.Id);") -eq '1') 'member guard is blocked'
$requested = Sql 'user-db' "SELECT event_id FROM user_outbox WHERE message_key='$($withdrawing.Id)' AND topic='user.withdrawal.requested';"
Check ((Sql 'match-db' "SELECT COUNT(*) FROM processed_event WHERE event_id='$requested';") -eq '1') 'EV-02 processing history exists once'
$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
do {
    $sent = Sql 'match-db' "SELECT COUNT(*) FROM match_outbox WHERE message_key='$($withdrawing.Id)' AND topic='match.withdrawal.completed' AND status='SENT';"
    if ($sent -eq '1') { break }
    Start-Sleep -Milliseconds 500
} while ((Get-Date) -lt $deadline)
Check ($sent -eq '1') 'EV-03 outbox is SENT once'
$completedPayload = Sql 'match-db' "SELECT payload FROM match_outbox WHERE message_key='$($withdrawing.Id)' AND topic='match.withdrawal.completed';"
$completed = ConvertFrom-Json $completedPayload
Check ($completed.deletedMatchIds -contains $ownedMatch) 'EV-03 contains deleted owned match ID'
Check ($completed.leftMatchIds -contains $remainingMatch) 'EV-03 contains left match ID'
$requestedPayload = Sql 'user-db' "SELECT payload FROM user_outbox WHERE event_id='$requested';"
$requestedEvent = ConvertFrom-Json $requestedPayload
Check ($completed.sagaId -eq $requestedEvent.sagaId) 'EV-02 and EV-03 saga IDs match'
$me = Request GET '/api/users/me' $null $withdrawing.Token
Check ($me.Status -eq 401 -and $me.Data.code -eq 'USER_ALREADY_WITHDRAWN') 'withdrawn member info is denied through gateway'
Check ((Request POST '/api/auth/login' @{ email = $withdrawing.Email; password = $password } $null).Status -eq 401) 'withdrawn member login is denied'
Check ((Request POST '/api/auth/refresh' @{ refreshToken = $withdrawing.Refresh } $null).Status -eq 401) 'withdrawn member refresh is denied'

# 두 요청을 기다리지 않고 전송하여 실제 MariaDB 조건부 UPDATE를 검증한다.
$client = [System.Net.Http.HttpClient]::new()
$client.Timeout = [TimeSpan]::FromSeconds(20)
$requests = @()
try {
    foreach ($index in 1..2) {
        $message = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::Delete, "$ApiUrl/api/users/me")
        $message.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $concurrent.Token)
        $message.Content = [System.Net.Http.StringContent]::new((ConvertTo-Json @{ password = $password } -Compress), [System.Text.Encoding]::UTF8, 'application/json')
        $requests += @{ Message = $message; Task = $client.SendAsync($message) }
    }
    $statuses = @($requests | ForEach-Object { [int]$_.Task.GetAwaiter().GetResult().StatusCode })
    Write-Host "Concurrent HTTP statuses: $($statuses -join ', ')"
    foreach ($request in $requests) {
        $response = $request.Task.GetAwaiter().GetResult()
        if ([int]$response.StatusCode -ne 202) {
            Write-Host "Concurrent rejection: $($response.Content.ReadAsStringAsync().GetAwaiter().GetResult())"
        }
    }
    Check (@($statuses | Where-Object { $_ -eq 202 }).Count -eq 1) 'concurrent requests accept exactly one withdrawal'
    Check (@($statuses | Where-Object { $_ -eq 409 }).Count -eq 1) 'concurrent duplicate request returns 409'
    Check ((Sql 'user-db' "SELECT COUNT(*) FROM user_outbox WHERE message_key='$($concurrent.Id)' AND topic='user.withdrawal.requested';") -eq '1') 'MariaDB concurrent requests create exactly one EV-02 outbox'
} finally {
    foreach ($request in $requests) {
        if ($request.Task.IsCompletedSuccessfully) { $request.Task.Result.Dispose() }
        $request.Message.Dispose()
    }
    $client.Dispose()
}
Write-Host "SUCCESS run=$nonce withdrawingUser=$($withdrawing.Id) remainingUser=$($remaining.Id) concurrentUser=$($concurrent.Id) ownedMatch=$ownedMatch remainingMatch=$remainingMatch fixture=$fixtureId"
Write-Host 'Test fixtures are retained for inspection. No existing data was deleted by this script.'
