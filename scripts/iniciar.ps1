<#
.SYNOPSIS
  Sobe o BioSolar Citrus: garante o PostgreSQL local rodando e inicia a API + dashboard
  em http://localhost:8080

.DESCRIPTION
  - Java: usa o JDK 21 portatil do projeto (instalado por setup-ambiente.ps1), mesmo que o
    Windows tenha outro Java (ex.: 17) no JAVA_HOME ou no PATH.
  - Banco: le o .env da raiz do projeto (BIOSOLAR_DB_URL etc.). Se o PostgreSQL portatil do
    BioSolar estiver rodando em outra porta, o .env e corrigido automaticamente.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\iniciar.ps1
  powershell -ExecutionPolicy Bypass -File scripts\iniciar.ps1 -H2      # plano B: sem PostgreSQL
#>
param(
    [switch]$H2,
    [string]$Ferramentas = (Join-Path $env:LOCALAPPDATA 'BioSolarDev')
)

$ErrorActionPreference = 'Stop'
$raiz = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'ambiente.ps1')

# Configuracao local da maquina (.env) ---------------------------------------
$arquivoEnv = Join-Path $raiz '.env'
Importar-DotEnv $arquivoEnv

# JDK 21 -----------------------------------------------------------------------
Configurar-Java21 -Ferramentas $Ferramentas

# Porta HTTP livre -------------------------------------------------------------
$portaHttp = if ($env:PORT) { [int]$env:PORT } else { 8080 }
if (Porta-EmUso $portaHttp) {
    throw "A porta $portaHttp ja esta em uso (outra instancia da API?). Encerre-a ou defina PORT no .env."
}

# PostgreSQL -------------------------------------------------------------------
if (-not $H2) {
    $url = Ler-UrlBanco $env:BIOSOLAR_DB_URL
    if (-not $url) { throw "BIOSOLAR_DB_URL invalida: $env:BIOSOLAR_DB_URL" }

    $pgBin = Join-Path $Ferramentas 'pgsql\bin'
    $pgData = Join-Path $Ferramentas 'pgdata'
    $temPortatil = (Test-Path (Join-Path $pgBin 'pg_ctl.exe')) -and (Test-Path (Join-Path $pgData 'PG_VERSION'))

    if ($temPortatil -and (Eh-HostLocal $url.Host)) {
        $portaAtiva = Obter-PortaClusterPortatil -PgBin $pgBin -PgData $pgData
        $porta = $url.Porta

        if ($portaAtiva) {
            $porta = $portaAtiva
        } else {
            if (Porta-EmUso $porta) {
                # Outra instalacao do PostgreSQL (ex.: servico do Windows) ocupa a porta configurada
                $livre = Obter-PortaLivre ($porta + 1)
                Write-Host "[i] Porta $porta ocupada por outro servico: o PostgreSQL do BioSolar usara a porta $livre."
                $porta = $livre
            }
            Write-Host "[..] Iniciando PostgreSQL do BioSolar na porta $porta ..."
            # Start-Process evita que o postgres herde o console deste script
            Start-Process -FilePath "$pgBin\pg_ctl.exe" -WindowStyle Hidden -ArgumentList @(
                '-D', "`"$pgData`"", '-l', "`"$(Join-Path $Ferramentas 'postgres.log')`"", '-o', "`"-p $porta`"", 'start')
        }

        $pronto = $false
        for ($i = 0; $i -lt 30 -and -not $pronto; $i++) {
            & "$pgBin\pg_isready.exe" -h localhost -p $porta | Out-Null
            $pronto = ($LASTEXITCODE -eq 0)
            if (-not $pronto) { Start-Sleep -Seconds 1 }
        }
        if (-not $pronto) { throw "PostgreSQL do BioSolar nao respondeu na porta $porta. Veja $(Join-Path $Ferramentas 'postgres.log')" }

        if ($porta -ne $url.Porta) {
            $env:BIOSOLAR_DB_URL = Montar-UrlBanco $url.Host $porta $url.Banco
            Definir-DotEnv $arquivoEnv 'BIOSOLAR_DB_URL' $env:BIOSOLAR_DB_URL
            Write-Host "[ok] .env atualizado: BIOSOLAR_DB_URL=$env:BIOSOLAR_DB_URL"
        }
        Write-Host "[ok] PostgreSQL do BioSolar disponivel na porta $porta."
    } else {
        Write-Host "[i] Usando o PostgreSQL configurado em BIOSOLAR_DB_URL ($($url.Host):$($url.Porta)/$($url.Banco))."
    }
}

Push-Location (Join-Path $raiz 'backend')
try {
    Write-Host ''
    Write-Host "  BioSolar Citrus -> http://localhost:$portaHttp  (Ctrl+C para encerrar)"
    Write-Host '  Antes de apresentar: Painel de Simulacao > "Restaurar cenario".'
    Write-Host ''
    if ($H2) {
        & .\mvnw.cmd -B spring-boot:run '-Dspring-boot.run.profiles=h2'
    } else {
        & .\mvnw.cmd -B spring-boot:run
    }
} finally {
    Pop-Location
}
