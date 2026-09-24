<#
.SYNOPSIS
  Sobe o BioSolar Citrus: garante o PostgreSQL local rodando e inicia a API + dashboard
  em http://localhost:8080

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\iniciar.ps1
  powershell -ExecutionPolicy Bypass -File scripts\iniciar.ps1 -H2      # plano B: sem PostgreSQL
#>
param(
    [switch]$H2,
    [string]$Ferramentas = (Join-Path $env:LOCALAPPDATA 'BioSolarDev'),
    [int]$PortaPostgres = 5432
)

$ErrorActionPreference = 'Stop'
$raiz = Split-Path -Parent $PSScriptRoot

# Variaveis opcionais do arquivo .env na raiz do projeto (modelo: .env.example)
$arquivoEnv = Join-Path $raiz '.env'
if (Test-Path $arquivoEnv) {
    Get-Content $arquivoEnv | ForEach-Object {
        $linha = $_.Trim()
        if ($linha -and -not $linha.StartsWith('#') -and $linha.Contains('=')) {
            $partes = $linha.Split('=', 2)
            Set-Item -Path ('Env:' + $partes[0].Trim()) -Value $partes[1].Trim()
        }
    }
    Write-Host '[ok] Variaveis carregadas de .env'
}
# A porta do PostgreSQL acompanha BIOSOLAR_DB_URL, se definida
if (-not $PSBoundParameters.ContainsKey('PortaPostgres') -and $env:BIOSOLAR_DB_URL -match 'localhost:(\d+)/') {
    $PortaPostgres = [int]$Matches[1]
}

# JDK: usa JAVA_HOME do sistema ou o JDK portatil instalado por setup-ambiente.ps1
if (-not $env:JAVA_HOME -and (Test-Path (Join-Path $Ferramentas 'jdk-21\bin\java.exe'))) {
    $env:JAVA_HOME = Join-Path $Ferramentas 'jdk-21'
}
if ($env:JAVA_HOME) {
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
} elseif (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw 'Java 21 nao encontrado. Execute scripts\setup-ambiente.ps1 ou instale um JDK 21.'
}

# PostgreSQL portatil (se existir e nao estiver rodando)
if (-not $H2) {
    $pgBin = Join-Path $Ferramentas 'pgsql\bin'
    $pgData = Join-Path $Ferramentas 'pgdata'
    if (Test-Path (Join-Path $pgBin 'pg_ctl.exe')) {
        & "$pgBin\pg_isready.exe" -h localhost -p $PortaPostgres | Out-Null
        if ($LASTEXITCODE -ne 0) {
            Write-Host "[..] Iniciando PostgreSQL local na porta $PortaPostgres ..."
            Start-Process -FilePath "$pgBin\pg_ctl.exe" -WindowStyle Hidden -ArgumentList @(
                '-D', "`"$pgData`"", '-l', "`"$(Join-Path $Ferramentas 'postgres.log')`"", '-o', "`"-p $PortaPostgres`"", 'start')
            for ($i = 0; $i -lt 30; $i++) {
                Start-Sleep -Seconds 1
                & "$pgBin\pg_isready.exe" -h localhost -p $PortaPostgres | Out-Null
                if ($LASTEXITCODE -eq 0) { break }
            }
        }
        Write-Host '[ok] PostgreSQL disponivel.'
    } else {
        Write-Host '[i] PostgreSQL portatil nao encontrado: usando o PostgreSQL configurado em BIOSOLAR_DB_URL (padrao localhost:5432/biosolar).'
    }
}

Push-Location (Join-Path $raiz 'backend')
try {
    Write-Host ''
    Write-Host '  BioSolar Citrus -> http://localhost:8080  (Ctrl+C para encerrar)'
    Write-Host ''
    if ($H2) {
        & .\mvnw.cmd -B spring-boot:run '-Dspring-boot.run.profiles=h2'
    } else {
        & .\mvnw.cmd -B spring-boot:run
    }
} finally {
    Pop-Location
}
