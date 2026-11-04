#Requires -Version 7.0
[CmdletBinding()]
param(
    [Alias('BASE_URL')]
    [string]$BaseUrl = $(if ($env:BASE_URL) { $env:BASE_URL } else { 'http://localhost:8000' }),
    [ValidatePattern('^(|[a-z0-9][a-z0-9-]{0,19})$')]
    [string]$WithdrawalId = '',
    [scriptblock]$OnUsersReady
)

$ErrorActionPreference = 'Stop'
$baseUri = $null
if (-not [Uri]::TryCreate($BaseUrl, [UriKind]::Absolute, [ref]$baseUri) -or
    $baseUri.Scheme -notin @('http','https') -or $baseUri.UserInfo -or $baseUri.Query -or $baseUri.Fragment) {
    throw 'BASE_URL must be an HTTP(S) Gateway URL without credentials, query, or fragment.'
}
$BaseUrl = $BaseUrl.TrimEnd('/')
# Public demonstration credentials only. Never use these accounts for personal data.
$demoPassword = '1234567890'
$withdrawalEmail = if ($WithdrawalId) { "withdraw-$WithdrawalId@demo.test" } else { 'withdraw@demo.test' }
$withdrawalNickname = if ($WithdrawalId) { "탈퇴시연유저_$WithdrawalId" } else { '탈퇴시연유저' }
$accounts = @(
    @{ Role='owner'; Email='a@b.c'; Nickname='테스트유저1'; District='MAPO'; SportTypes=@('FUTSAL') },
    @{ Role='participant'; Email='b@c.d'; Nickname='테스트유저2'; District='GANGNAM'; SportTypes=@('FUTSAL','BASKETBALL') },
    @{ Role='withdrawal'; Email=$withdrawalEmail; Nickname=$withdrawalNickname; District='MAPO'; SportTypes=@('FUTSAL') }
)

function Invoke-DemoApi([string]$method, [string]$path, $body, [string]$token) {
    $options = @{ Uri="$BaseUrl$path"; Method=$method; TimeoutSec=20; SkipHttpErrorCheck=$true }
    if ($null -ne $body) {
        $options.ContentType = 'application/json; charset=utf-8'
        $options.Body = [Text.Encoding]::UTF8.GetBytes((ConvertTo-Json $body -Depth 5 -Compress))
    }
    if ($token) { $options.Headers = @{ Authorization="Bearer $token" } }
    try { $response = Invoke-WebRequest @options }
    catch { throw "Gateway request failed: $method $path. Check BASE_URL and service health. No account was reset." }
    $data = $null
    if ($response.Content) {
        try { $data = ConvertFrom-Json $response.Content }
        catch { throw "Gateway returned non-JSON content: $method $path HTTP $([int]$response.StatusCode)." }
    }
    return @{ Status=[int]$response.StatusCode; Data=$data }
}

$sessions = [ordered]@{}
foreach ($account in $accounts) {
    $credentials = @{ email=$account.Email; password=$demoPassword }
    $login = Invoke-DemoApi POST '/api/auth/login' $credentials $null
    $action = 'REUSED'
    if ($login.Status -eq 401) {
        # 401 can mean missing, wrong password, pending, or withdrawn. Signup distinguishes
        # a missing account without changing an existing account's password or state.
        $signup = Invoke-DemoApi POST '/api/users' @{
            email=$account.Email; password=$demoPassword; gender='MALE'
            nickname=$account.Nickname; district=$account.District; sportTypes=$account.SportTypes
        } $null
        if ($signup.Status -eq 409) {
            throw "Account conflict: $($account.Email), $($signup.Data.code). Existing credentials/profile/state were not changed. For a withdrawn demo account, use a new -WithdrawalId."
        }
        if ($signup.Status -ne 201) {
            throw "Signup failed for $($account.Email): HTTP $($signup.Status). Existing data was not reset."
        }
        $action = 'CREATED'
        $login = Invoke-DemoApi POST '/api/auth/login' $credentials $null
    }
    if ($login.Status -ne 200) {
        throw "Login failed for $($account.Email): HTTP $($login.Status). Existing data was not reset."
    }
    if (-not $login.Data.userId -or [long]$login.Data.userId -le 0 -or
        $login.Data.tokenType -ne 'Bearer' -or [string]::IsNullOrWhiteSpace($login.Data.accessToken)) {
        throw "Invalid login response for $($account.Email)."
    }
    $info = Invoke-DemoApi GET '/api/users/me' $null $login.Data.accessToken
    if ($info.Status -ne 200) { throw "Authenticated profile check failed for $($account.Email): HTTP $($info.Status)." }
    $actualSports = (@($info.Data.preferenceSports) | Sort-Object) -join ','
    $expectedSports = (@($account.SportTypes) | Sort-Object) -join ','
    if ($info.Data.email -ne $account.Email -or $info.Data.nickname -cne $account.Nickname -or
        $info.Data.gender -ne 'MALE' -or $info.Data.district -ne $account.District -or $actualSports -ne $expectedSports) {
        throw "Profile conflict for $($account.Email). Expected demo profile differs; no profile was overwritten."
    }
    $sessions[$account.Role] = [pscustomobject]@{
        Role=$account.Role; Email=$account.Email; UserId=[long]$login.Data.userId
        AccessToken=[string]$login.Data.accessToken
        Nickname=$account.Nickname; District=$account.District; SportTypes=$account.SportTypes
    }
    Write-Host ("{0}: {1} userId={2} {3}; login=200 profile=200" -f $account.Role,$account.Email,$login.Data.userId,$action)
}
Write-Host 'Demo users ready. Access/Refresh tokens and passwords are not printed or saved.'
if ($OnUsersReady) {
    # The match seed can consume these sessions in the same PowerShell process.
    # Do not serialize or print AccessToken. A login normally rotates the stored RT.
    & $OnUsersReady $sessions
}
