<#
.SYNOPSIS
  Prepara um ambiente portatil (sem administrador) para o BioSolar Citrus:
  JDK 21 (Temurin), Maven 3.9 e PostgreSQL 17 com o banco "biosolar" criado.

.DESCRIPTION
  Tudo e instalado em %LOCALAPPDATA%\BioSolarDev (fora do OneDrive, para evitar
  sincronizacao de binarios e dos arquivos de dados do PostgreSQL).
  O script e idempotente: pode ser executado novamente sem reinstalar nada.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\setup-ambiente.ps1
  powershell -ExecutionPolicy Bypass -File scripts\setup-ambiente.ps1 -SemPostgres
#>
param(
    [string]$Destino = (Join-Path $env:LOCALAPPDATA 'BioSolarDev'),
    [int]$PortaPostgres = 5432,
    [switch]$SemPostgres
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

# Variaveis opcionais do arquivo .env na raiz do projeto (modelo: .env.example)
$raiz = Split-Path -Parent $PSScriptRoot
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

$JdkUrl      = 'https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk'
$MavenVersao = '3.9.11'
$MavenUrl    = "https://repo1.maven.org/maven2/org/apache/maven/apache-maven/$MavenVersao/apache-maven-$MavenVersao-bin.zip"
$PgUrl       = 'https://get.enterprisedb.com/postgresql/postgresql-17.6-1-windows-x64-binaries.zip'

# Credenciais APENAS do cluster local de desenvolvimento (sobrescreva por variavel de ambiente).
$PgSuperSenha = if ($env:BIOSOLAR_PG_SUPER_SENHA) { $env:BIOSOLAR_PG_SUPER_SENHA } else { 'postgres' }
$DbNome       = if ($env:BIOSOLAR_DB_NOME)  { $env:BIOSOLAR_DB_NOME }  else { 'biosolar' }
$DbUsuario    = if ($env:BIOSOLAR_DB_USUARIO) { $env:BIOSOLAR_DB_USUARIO } else { 'biosolar' }
$DbSenha      = if ($env:BIOSOLAR_DB_SENHA) { $env:BIOSOLAR_DB_SENHA } else { 'biosolar' }

New-Item -ItemType Directory -Force $Destino | Out-Null

function Instalar-Zip {
    param([string]$Nome, [string]$Url, [string]$PastaFinal, [string]$ArquivoTeste, [string[]]$Excluir = @())

    if (Test-Path (Join-Path $PastaFinal $ArquivoTeste)) {
        Write-Host "[ok] $Nome ja instalado em $PastaFinal"
        return
    }
    $zip = Join-Path $Destino "$Nome.zip"
    $tmp = Join-Path $Destino "$Nome-tmp"

    Write-Host "[..] Baixando $Nome ..."
    Invoke-WebRequest -Uri $Url -OutFile $zip -UseBasicParsing

    Write-Host "[..] Extraindo $Nome ..."
    if (Test-Path $tmp) { Remove-Item -Recurse -Force $tmp }
    New-Item -ItemType Directory -Force $tmp | Out-Null
    $tarArgs = @('-xf', $zip, '-C', $tmp)
    foreach ($e in $Excluir) { $tarArgs += @('--exclude', $e) }
    & "$env:SystemRoot\System32\tar.exe" @tarArgs
    if ($LASTEXITCODE -ne 0) { throw "Falha ao extrair $Nome" }

    $interna = Get-ChildItem $tmp -Directory | Select-Object -First 1
    if (Test-Path $PastaFinal) { Remove-Item -Recurse -Force $PastaFinal }
    Move-Item $interna.FullName $PastaFinal
    Remove-Item -Recurse -Force $tmp
    Remove-Item -Force $zip
    Write-Host "[ok] $Nome instalado em $PastaFinal"
}

# 1. JDK 21 ------------------------------------------------------------------
$jdkHome = Join-Path $Destino 'jdk-21'
Instalar-Zip -Nome 'jdk-21' -Url $JdkUrl -PastaFinal $jdkHome -ArquivoTeste 'bin\java.exe'

# 2. Maven (usado apenas para gerar/atualizar o Maven Wrapper) ----------------
$mavenHome = Join-Path $Destino 'maven'
Instalar-Zip -Nome 'maven' -Url $MavenUrl -PastaFinal $mavenHome -ArquivoTeste 'bin\mvn.cmd'

# 3. PostgreSQL 17 -----------------------------------------------------------
if (-not $SemPostgres) {
    $pgHome = Join-Path $Destino 'pgsql'
    Instalar-Zip -Nome 'pgsql' -Url $PgUrl -PastaFinal $pgHome -ArquivoTeste 'bin\pg_ctl.exe' `
        -Excluir @('pgsql/pgAdmin 4', 'pgsql/symbols', 'pgsql/doc', 'pgsql/StackBuilder')

    $pgBin  = Join-Path $pgHome 'bin'
    $pgData = Join-Path $Destino 'pgdata'
    $pgLog  = Join-Path $Destino 'postgres.log'

    if (-not (Test-Path (Join-Path $pgData 'PG_VERSION'))) {
        Write-Host '[..] Inicializando cluster PostgreSQL ...'
        $pwFile = Join-Path $Destino 'pw.tmp'
        [IO.File]::WriteAllText($pwFile, $PgSuperSenha)
        & "$pgBin\initdb.exe" -D $pgData -U postgres "--pwfile=$pwFile" -A scram-sha-256 -E UTF8 --locale=C | Out-Host
        Remove-Item -Force $pwFile
        if ($LASTEXITCODE -ne 0) { throw 'Falha no initdb' }
    }

    & "$pgBin\pg_ctl.exe" -D $pgData status | Out-Null
    if ($LASTEXITCODE -ne 0) {
        & "$pgBin\pg_isready.exe" -h localhost -p $PortaPostgres | Out-Null
        if ($LASTEXITCODE -eq 0) {
            throw ("Ja existe outro PostgreSQL usando a porta $PortaPostgres neste computador. Opcoes: " +
                "(1) crie o banco nele (README, secao 'Banco de dados') ou " +
                "(2) use outra porta: copie .env.example para .env, defina " +
                "BIOSOLAR_DB_URL=jdbc:postgresql://localhost:5433/biosolar e rode este script novamente.")
        }
        Write-Host "[..] Iniciando PostgreSQL na porta $PortaPostgres ..."
        # Start-Process evita que o postgres herde o pipe de saida deste script (o que o travaria)
        Start-Process -FilePath "$pgBin\pg_ctl.exe" -WindowStyle Hidden `
            -ArgumentList @('-D', "`"$pgData`"", '-l', "`"$pgLog`"", '-o', "`"-p $PortaPostgres`"", 'start')
        $pronto = $false
        for ($i = 0; $i -lt 30 -and -not $pronto; $i++) {
            Start-Sleep -Seconds 1
            & "$pgBin\pg_isready.exe" -h localhost -p $PortaPostgres | Out-Null
            $pronto = ($LASTEXITCODE -eq 0)
        }
        if (-not $pronto) { throw "PostgreSQL nao respondeu. Veja $pgLog" }
    }

    $env:PGPASSWORD = $PgSuperSenha
    $psql = @("$pgBin\psql.exe", '-h', 'localhost', '-p', "$PortaPostgres", '-U', 'postgres', '-v', 'ON_ERROR_STOP=1', '-tA')
    $temRole = & $psql[0] $psql[1..($psql.Length - 1)] -c "SELECT 1 FROM pg_roles WHERE rolname = '$DbUsuario'"
    if ("$temRole".Trim() -ne '1') {
        & $psql[0] $psql[1..($psql.Length - 1)] -c "CREATE ROLE $DbUsuario LOGIN PASSWORD '$DbSenha'" | Out-Host
    }
    $temDb = & $psql[0] $psql[1..($psql.Length - 1)] -c "SELECT 1 FROM pg_database WHERE datname = '$DbNome'"
    if ("$temDb".Trim() -ne '1') {
        & $psql[0] $psql[1..($psql.Length - 1)] -c "CREATE DATABASE $DbNome OWNER $DbUsuario ENCODING 'UTF8'" | Out-Host
    }
    Remove-Item Env:\PGPASSWORD
    Write-Host "[ok] PostgreSQL pronto: jdbc:postgresql://localhost:$PortaPostgres/$DbNome (usuario $DbUsuario)"
}

Write-Host ''
Write-Host '============================================================'
Write-Host ' Ambiente pronto. Para subir a aplicacao:'
Write-Host '   powershell -ExecutionPolicy Bypass -File scripts\iniciar.ps1'
Write-Host '============================================================'
