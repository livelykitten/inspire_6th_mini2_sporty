#Requires -Version 7.0
param(
    [string]$ApiUrl='http://localhost:8000',
    [string]$UserUrl='http://localhost:8001',
    [int]$TimeoutSeconds=90,
    [Parameter(Mandatory)][scriptblock]$RestoreNormalMode
)
$ErrorActionPreference='Stop'
$repoRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Set-Location $repoRoot
$nonce=[guid]::NewGuid().ToString('N').Substring(0,12)
$password='W32integration123!'
function Check($condition,[string]$message) {
    if(-not $condition){throw $message}
    Write-Host "PASS: $message"
}
function Request([string]$method,[string]$path,$body,$token) {
    $options=@{Uri="$ApiUrl$path";Method=$method;SkipHttpErrorCheck=$true;TimeoutSec=15}
    if($null -ne $body){$options.ContentType='application/json';$options.Body=ConvertTo-Json $body -Depth 10 -Compress}
    if($token){$options.Headers=@{Authorization="Bearer $token"}}
    $response=Invoke-WebRequest @options
    $data=if($response.Content){ConvertFrom-Json $response.Content}else{$null}
    return @{Status=[int]$response.StatusCode;Data=$data}
}
function Sql([string]$database,[string]$query) {
    $result=$query | & docker compose exec -T $database sh -c 'exec mariadb -u"$MARIADB_USER" -p"$MARIADB_PASSWORD" "$MARIADB_DATABASE" --batch --skip-column-names' 2>&1
    if($LASTEXITCODE -ne 0){throw "DB query failed: $result"}
    return (($result | ForEach-Object {"$_"}) -join "`n").Trim()
}
function RedisToken([long]$memberId) {
    $value=& docker compose exec -T redis redis-cli --raw GET "refresh:$memberId"
    if($LASTEXITCODE -ne 0){throw 'Redis query failed'}
    return "$value".Trim()
}
function State([long]$memberId){return Invoke-RestMethod "$UserUrl/internal/users/$memberId/status" -TimeoutSec 5}
function NewMember([string]$label) {
    $email="w32-$nonce-$label@example.com"
    $signup=Request POST '/api/users' @{email=$email;password=$password;gender='MALE';nickname="w32_${nonce}_$label";district='GANGNAM';sportTypes=@()} $null
    Check ($signup.Status -eq 201) "$label signup 201"
    $login=Request POST '/api/auth/login' @{email=$email;password=$password} $null
    Check ($login.Status -eq 200) "$label login 200"
    return @{Id=[long]$login.Data.userId;Token=$login.Data.accessToken;Refresh=$login.Data.refreshToken;Email=$email}
}
function CreateMatch($member,[string]$label,[int]$capacity) {
    $response=Request POST '/api/matches' @{serviceId=$fixtureId;title="w32-$nonce-$label";description='W32 integration fixture';startAt=(Get-Date).AddDays(2).ToString('yyyy-MM-ddTHH:mm:ss');endAt=(Get-Date).AddDays(2).AddHours(1).ToString('yyyy-MM-ddTHH:mm:ss');maxParticipant=$capacity;skillLevel='BEGINNER';sportType='SOCCER';genderGroup='MIXED'} $member.Token
    Check ($response.Status -eq 201) "$label match creation 201"
    return [long]$response.Data
}
function Publish([string]$topic,[long]$memberId,[string]$payload) {
    "$memberId|$payload" | & docker compose exec -T kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic $topic --property parse.key=true --property 'key.separator=|'
    if($LASTEXITCODE -ne 0){throw 'Kafka publish failed'}
}
function Snapshot {
    return (Sql 'match-db' "SELECT * FROM ``match`` WHERE id IN ($ownedMatch,$remainingMatch) ORDER BY id;") + "`n--participants--`n" + (Sql 'match-db' "SELECT * FROM match_participant WHERE match_id IN ($ownedMatch,$remainingMatch) ORDER BY match_participant_id;") + "`n--guard--`n" + (Sql 'match-db' "SELECT user_id,is_blocked+0 FROM match_user_guard WHERE user_id=$($withdrawing.Id);")
}
function Wait-State([long]$memberId,[string]$expected) {
    $deadline=(Get-Date).AddSeconds($TimeoutSeconds)
    do{$current=State $memberId;if($current.status -eq $expected){return $current};Start-Sleep -Milliseconds 300}while((Get-Date) -lt $deadline)
    throw "Member did not reach $expected"
}
function Wait-Outbox([string]$topic,[string]$sagaId) {
    $deadline=(Get-Date).AddSeconds($TimeoutSeconds)
    $filter="message_key='$($withdrawing.Id)' AND topic='$topic' AND JSON_UNQUOTE(JSON_EXTRACT(payload,'$.sagaId'))='$sagaId'"
    do{$sent=Sql 'match-db' "SELECT COUNT(*) FROM match_outbox WHERE $filter AND status='SENT';";if($sent -eq '1'){return Sql 'match-db' "SELECT payload FROM match_outbox WHERE $filter;"};Start-Sleep -Milliseconds 300}while((Get-Date) -lt $deadline)
    throw "Outbox did not reach SENT: $topic"
}

Write-Host "Run: $nonce (tokens and passwords are not printed)"
$withdrawing=NewMember 'withdraw'
$remaining=NewMember 'remain'
$fixtureId=[long]('9'+(Get-Date -Format 'yyMMddHHmmss'))
$null=Sql 'match-db' "INSERT INTO facility_service_snapshot(service_id,name,location_name,region,is_free,active,source_updated_at,updated_at) VALUES($fixtureId,'w32-$nonce','integration','GANGNAM',1,1,NOW(),NOW());"
$ownedMatch=CreateMatch $withdrawing 'owned' 3
$remainingMatch=CreateMatch $remaining 'remaining' 2
Check ((Request POST "/api/matches/$remainingMatch/participants" $null $withdrawing.Token).Status -eq 201) 'test member joins other owner match'
$detail=Request GET "/api/matches/$remainingMatch" $null $remaining.Token
Check ($detail.Data.status -eq 'CLOSED' -and $detail.Data.currentParticipantCount -eq 2) 'full match CLOSED with two participants'
$before=Snapshot
$tokenBefore=RedisToken $withdrawing.Id
Check ($tokenBefore -eq $withdrawing.Refresh) 'initial Redis RT matches login RT'
$accepted=Request DELETE '/api/users/me' @{password=$password} $withdrawing.Token
Check ($accepted.Status -eq 202) 'failure scenario withdrawal accepted 202 through Gateway'
$requestedPayload=Sql 'user-db' "SELECT payload FROM user_outbox WHERE message_key='$($withdrawing.Id)' ORDER BY id DESC LIMIT 1;"
$requested=ConvertFrom-Json $requestedPayload
Check (-not [string]::IsNullOrWhiteSpace($requested.sagaId)) 'real EV-02 Saga ID recorded'
$restored=Wait-State $withdrawing.Id 'ACTIVE'
Check ($null -eq $restored.sagaId) 'EV-04 restores ACTIVE and clears Saga ID'
Check ((Sql 'user-db' "SELECT withdrawn_at IS NULL FROM user WHERE id=$($withdrawing.Id);") -eq '1') 'compensated member has no withdrawal timestamp'
$failedPayload=Wait-Outbox 'match.withdrawal.failed' $requested.sagaId
$failed=ConvertFrom-Json $failedPayload
Check ($failed.sagaId -eq $requested.sagaId -and $failed.userId -eq $withdrawing.Id) 'EV-02 and real EV-04 Saga/member match'
Check ($failed.reason -eq 'MATCH_CLEANUP_FAILED') 'failure event reason is MATCH_CLEANUP_FAILED'
Check ($failed.eventId -ne $requested.eventId) 'EV-04 event ID differs from EV-02'
Check ((Sql 'user-db' "SELECT status FROM user_outbox WHERE event_id='$($requested.eventId)';") -eq 'SENT') 'EV-02 Outbox SENT'
Check ((Snapshot) -ceq $before) 'all test match/participant rows and Guard exactly preserved after rollback'
Check ((Sql 'match-db' "SELECT COUNT(*) FROM processed_event WHERE event_id='$($requested.eventId)';") -eq '1') 'EV-02 processing marker exactly once'
Check ((Sql 'match-db' "SELECT COUNT(*) FROM match_outbox WHERE message_key='$($withdrawing.Id)' AND topic='match.withdrawal.completed';") -eq '0') 'failed Saga produces no EV-03'
Check ((RedisToken $withdrawing.Id) -eq $tokenBefore) 'existing Redis RT unchanged after compensation'
Check ((Request POST '/api/auth/refresh' @{refreshToken=$withdrawing.Refresh} $null).Status -eq 200) 'existing RT refresh 200 through Gateway'
Check ((RedisToken $withdrawing.Id) -eq $tokenBefore) 'refresh does not replace RT'
Check ((Request GET '/api/users/me' $null $withdrawing.Token).Status -eq 200) 'existing AT reads member info 200 after compensation'
Check ((Request GET '/api/matches/me' $null $withdrawing.Token).Status -eq 200) 'restored member can use match API'
Publish 'user.withdrawal.requested' $withdrawing.Id $requestedPayload
Publish 'match.withdrawal.failed' $withdrawing.Id $failedPayload
Start-Sleep -Seconds 3
Check ((State $withdrawing.Id).status -eq 'ACTIVE') 'duplicate EV-02 and EV-04 do not change restored member'
Check ((Sql 'match-db' "SELECT COUNT(*) FROM match_outbox WHERE message_key='$($withdrawing.Id)' AND topic='match.withdrawal.failed';") -eq '1') 'duplicate delivery creates no second EV-04'
Check ((Snapshot) -ceq $before) 'duplicate deliveries preserve matches and participants'

# Finish compensation checks before login, which normally rotates the Redis RT.
$login=Request POST '/api/auth/login' @{email=$withdrawing.Email;password=$password} $null
Check ($login.Status -eq 200) 'compensated member login 200'
$withdrawing.Token=$login.Data.accessToken
$withdrawing.Refresh=$login.Data.refreshToken

Write-Host 'Restoring normal match-service mode before success regression'
& $RestoreNormalMode
$accepted=Request DELETE '/api/users/me' @{password=$password} $withdrawing.Token
Check ($accepted.Status -eq 202) 'same member starts a new withdrawal after compensation'
$newRequestedPayload=Sql 'user-db' "SELECT payload FROM user_outbox WHERE message_key='$($withdrawing.Id)' ORDER BY id DESC LIMIT 1;"
$newRequested=ConvertFrom-Json $newRequestedPayload
Check ($newRequested.sagaId -ne $requested.sagaId) 'retry creates a new Saga ID'
$withdrawn=Wait-State $withdrawing.Id 'WITHDRAWN'
Check ($null -eq $withdrawn.sagaId) 'normal regression confirms WITHDRAWN and clears Saga ID'
$completedPayload=Wait-Outbox 'match.withdrawal.completed' $newRequested.sagaId
$completed=ConvertFrom-Json $completedPayload
Check ($completed.deletedMatchIds -contains $ownedMatch -and $completed.leftMatchIds -contains $remainingMatch) 'new Saga EV-03 contains owned and joined match IDs'
Check ((Request GET "/api/matches/$ownedMatch" $null $null).Status -eq 404) 'normal withdrawal deletes owned match'
$after=Request GET "/api/matches/$remainingMatch" $null $remaining.Token
Check ($after.Status -eq 200 -and $after.Data.status -eq 'RECRUITING' -and $after.Data.currentParticipantCount -eq 1) 'normal withdrawal preserves and reopens other owner match'
Check ((Sql 'match-db' "SELECT COUNT(*) FROM match_participant WHERE user_id=$($withdrawing.Id);") -eq '0') 'normal withdrawal removes member participation rows'
Check ((Sql 'match-db' "SELECT is_blocked+0 FROM match_user_guard WHERE user_id=$($withdrawing.Id);") -eq '1') 'normal withdrawal blocks Guard'
$deadline=(Get-Date).AddSeconds($TimeoutSeconds)
do{$token=RedisToken $withdrawing.Id;if(-not $token){break};Start-Sleep -Milliseconds 300}while((Get-Date) -lt $deadline)
Check (-not $token) 'normal withdrawal deletes Redis RT'
$me=Request GET '/api/users/me' $null $withdrawing.Token
Check ($me.Status -eq 401 -and $me.Data.code -eq 'USER_ALREADY_WITHDRAWN') 'old AT denied after WITHDRAWN'
Check ((Request POST '/api/auth/refresh' @{refreshToken=$withdrawing.Refresh} $null).Status -eq 401) 'old RT denied after WITHDRAWN'
Check ((Request POST '/api/auth/login' @{email=$withdrawing.Email;password=$password} $null).Status -eq 401) 'login denied after WITHDRAWN'
Publish 'match.withdrawal.failed' $withdrawing.Id $failedPayload
Publish 'match.withdrawal.completed' $withdrawing.Id $completedPayload
Start-Sleep -Seconds 3
Check ((State $withdrawing.Id).status -eq 'WITHDRAWN') 'late previous failure and duplicate completion cannot restore withdrawn member'
Check (-not (RedisToken $withdrawing.Id)) 'late/duplicate events do not restore RT'
Write-Host "SUCCESS run=$nonce withdrawingUser=$($withdrawing.Id) remainingUser=$($remaining.Id) ownedMatch=$ownedMatch remainingMatch=$remainingMatch fixture=$fixtureId failedSaga=$($requested.sagaId) successSaga=$($newRequested.sagaId)"
Write-Host 'Failure injection disabled. Test fixtures retained; no pre-existing member/match data reset.'
