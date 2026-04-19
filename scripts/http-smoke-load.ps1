param(
    [Parameter(Mandatory = $true)]
    [string]$BaseUrl,

    [Parameter(Mandatory = $true)]
    [string]$DatasourceName,

    [Parameter(Mandatory = $true)]
    [string]$Sql,

    [int]$Iterations = 20,
    [int]$Concurrency = 4
)

$ErrorActionPreference = "Stop"

function Invoke-JsonPost {
    param(
        [string]$Url,
        [hashtable]$Body
    )

    $json = $Body | ConvertTo-Json -Depth 5
    return Invoke-RestMethod -Method Post -Uri $Url -ContentType "application/json" -Body $json
}

Write-Host "== Smoke checks =="
$health = Invoke-RestMethod -Method Get -Uri "$BaseUrl/health"
$status = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/status"
Write-Host "/health success=$($health.success) code=$($health.code)"
Write-Host "/api/status success=$($status.success) code=$($status.code)"

Write-Host ""
Write-Host "== Query load =="

$jobs = @()
$results = [System.Collections.Generic.List[object]]::new()
$started = Get-Date

for ($i = 0; $i -lt $Iterations; $i++) {
    while (($jobs | Where-Object { $_.State -eq "Running" }).Count -ge $Concurrency) {
        $done = Wait-Job -Job $jobs -Any
        $output = Receive-Job -Job $done
        $results.Add($output)
        Remove-Job -Job $done
        $jobs = $jobs | Where-Object { $_.Id -ne $done.Id }
    }

    $jobs += Start-Job -ScriptBlock {
        param($BaseUrl, $DatasourceName, $Sql)
        $begin = Get-Date
        try {
            $json = @{ datasourceName = $DatasourceName; sql = $Sql } | ConvertTo-Json -Depth 5
            $resp = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/query" -ContentType "application/json" -Body $json
            [pscustomobject]@{
                Success = [bool]$resp.success
                Code = [string]$resp.code
                CostMs = [int]((Get-Date) - $begin).TotalMilliseconds
            }
        } catch {
            [pscustomobject]@{
                Success = $false
                Code = "REQUEST_FAILED"
                CostMs = [int]((Get-Date) - $begin).TotalMilliseconds
            }
        }
    } -ArgumentList $BaseUrl, $DatasourceName, $Sql
}

while ($jobs.Count -gt 0) {
    $done = Wait-Job -Job $jobs -Any
    $output = Receive-Job -Job $done
    $results.Add($output)
    Remove-Job -Job $done
    $jobs = $jobs | Where-Object { $_.Id -ne $done.Id }
}

$elapsed = [int]((Get-Date) - $started).TotalMilliseconds
$successes = @($results | Where-Object { $_.Success }).Count
$failures = $results.Count - $successes
$avg = if ($results.Count -gt 0) { [int](($results | Measure-Object -Property CostMs -Average).Average) } else { 0 }
$max = if ($results.Count -gt 0) { [int](($results | Measure-Object -Property CostMs -Maximum).Maximum) } else { 0 }

Write-Host "requests=$($results.Count)"
Write-Host "successes=$successes"
Write-Host "failures=$failures"
Write-Host "totalMs=$elapsed"
Write-Host "avgMs=$avg"
Write-Host "maxMs=$max"
