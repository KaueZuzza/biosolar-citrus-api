<#
.SYNOPSIS
  Funcoes compartilhadas por setup-ambiente.ps1 e iniciar.ps1 (carregado com ". $PSScriptRoot\ambiente.ps1").
  - .env: le e grava a configuracao local da maquina (ex.: porta do PostgreSQL do BioSolar)
  - Java: escolhe um JDK 21+, priorizando o JDK portatil do projeto
  - Portas: descobre a porta do cluster portatil e detecta portas ocupadas
#>

$script:UrlPadraoBanco = 'jdbc:postgresql://localhost:5432/biosolar'

function Ler-DotEnv {
    param([string]$Arquivo)
    $valores = [ordered]@{}
    if (-not (Test-Path $Arquivo)) { return $valores }
    foreach ($linha in Get-Content -Path $Arquivo -Encoding UTF8) {
        $l = $linha.Trim()
        if ($l -eq '' -or $l.StartsWith('#')) { continue }
        $i = $l.IndexOf('=')
        if ($i -lt 1) { continue }
        $chave = $l.Substring(0, $i).Trim()
        $valor = $l.Substring($i + 1).Trim().Trim('"').Trim("'")
        $valores[$chave] = $valor
    }
    return $valores
}

<# Carrega o .env no ambiente do processo. Variaveis ja definidas no sistema tem prioridade. #>
function Importar-DotEnv {
    param([string]$Arquivo)
    $valores = Ler-DotEnv $Arquivo
    foreach ($chave in $valores.Keys) {
        if (-not [Environment]::GetEnvironmentVariable($chave, 'Process')) {
            [Environment]::SetEnvironmentVariable($chave, $valores[$chave], 'Process')
        }
    }
    if ($valores.Count -gt 0) { Write-Host "[ok] Configuracao local carregada de $Arquivo" }
}

<# Cria ou atualiza uma chave no .env preservando as demais linhas e comentarios. #>
function Definir-DotEnv {
    param([string]$Arquivo, [string]$Chave, [string]$Valor)
    $linhas = @()
    if (Test-Path $Arquivo) { $linhas = @(Get-Content -Path $Arquivo -Encoding UTF8) }
    $achou = $false
    for ($i = 0; $i -lt $linhas.Count; $i++) {
        if ($linhas[$i] -match "^\s*$([regex]::Escape($Chave))\s*=") {
            $linhas[$i] = "$Chave=$Valor"
            $achou = $true
        }
    }
    if (-not $achou) { $linhas += "$Chave=$Valor" }
    # UTF-8 sem BOM: o Spring Boot tambem le este arquivo (spring.config.import)
    [IO.File]::WriteAllLines($Arquivo, [string[]]$linhas, (New-Object Text.UTF8Encoding($false)))
}

<# Garante que o .env exista (copiado de .env.example na primeira execucao). #>
function Garantir-DotEnv {
    param([string]$Raiz)
    $arquivo = Join-Path $Raiz '.env'
    $exemplo = Join-Path $Raiz '.env.example'
    if (-not (Test-Path $arquivo) -and (Test-Path $exemplo)) {
        Copy-Item $exemplo $arquivo
        Write-Host "[ok] .env criado a partir de .env.example"
    }
    return $arquivo
}

<# Versao principal de um JDK a partir do arquivo "release" (sem executar o java). #>
function Obter-VersaoJava {
    param([string]$JavaHome)
    if (-not $JavaHome) { return 0 }
    $release = Join-Path $JavaHome 'release'
    if (-not (Test-Path $release)) { return 0 }
    $linha = Select-String -Path $release -Pattern '^JAVA_VERSION="([0-9]+)' | Select-Object -First 1
    if (-not $linha) { return 0 }
    return [int]$linha.Matches[0].Groups[1].Value
}

<#
  Escolhe o JDK usado pelo Maven Wrapper, nesta ordem:
    1) JDK 21 portatil do projeto (%LOCALAPPDATA%\BioSolarDev\jdk-21), mesmo que o sistema tenha outro Java
    2) JAVA_HOME do sistema, se for 21 ou superior
    3) java do PATH, se for 21 ou superior
  Define JAVA_HOME e coloca o bin no inicio do PATH somente para este processo.
#>
function Configurar-Java21 {
    param([string]$Ferramentas)
    $candidatos = @()
    $candidatos += Join-Path $Ferramentas 'jdk-21'
    if ($env:JAVA_HOME) { $candidatos += $env:JAVA_HOME }
    $javaPath = Get-Command java -ErrorAction SilentlyContinue
    if ($javaPath) { $candidatos += Split-Path -Parent (Split-Path -Parent $javaPath.Source) }

    foreach ($jdk in $candidatos) {
        if (-not (Test-Path (Join-Path $jdk 'bin\java.exe'))) { continue }
        $versao = Obter-VersaoJava $jdk
        if ($versao -ge 21) {
            $env:JAVA_HOME = $jdk
            $env:Path = "$jdk\bin;$env:Path"
            Write-Host "[ok] Java ${versao}: $jdk"
            return
        }
        Write-Host "[i] Ignorando Java $versao em $jdk (o projeto exige Java 21)."
    }
    throw 'Java 21 nao encontrado. Execute scripts\setup-ambiente.ps1 (instala um JDK 21 portatil) ou instale um JDK 21.'
}

<# Extrai host e porta de uma URL jdbc:postgresql://host:porta/banco. #>
function Ler-UrlBanco {
    param([string]$Url)
    if (-not $Url) { $Url = $script:UrlPadraoBanco }
    if ($Url -match '^jdbc:postgresql://([^:/?]+)(?::(\d+))?/([^?]+)') {
        $porta = if ($Matches[2]) { [int]$Matches[2] } else { 5432 }
        return [pscustomobject]@{ Host = $Matches[1]; Porta = $porta; Banco = $Matches[3] }
    }
    return $null
}

function Eh-HostLocal {
    param([string]$HostBanco)
    return @('localhost', '127.0.0.1', '::1') -contains $HostBanco
}

<# Porta em que o cluster portatil esta rodando (4a linha do postmaster.pid) ou $null. #>
function Obter-PortaClusterPortatil {
    param([string]$PgBin, [string]$PgData)
    if (-not (Test-Path (Join-Path $PgBin 'pg_ctl.exe'))) { return $null }
    & "$PgBin\pg_ctl.exe" -D $PgData status | Out-Null
    if ($LASTEXITCODE -ne 0) { return $null }
    $arquivoPid = Join-Path $PgData 'postmaster.pid'
    if (-not (Test-Path $arquivoPid)) { return $null }
    $linhas = @(Get-Content $arquivoPid)
    if ($linhas.Count -ge 4 -and $linhas[3] -match '^\d+$') { return [int]$linhas[3] }
    return $null
}

function Porta-EmUso {
    param([int]$Porta)
    return [bool](Get-NetTCPConnection -State Listen -LocalPort $Porta -ErrorAction SilentlyContinue)
}

<# Primeira porta livre a partir de $Inicio (usada quando outra instalacao do PostgreSQL ocupa a 5432). #>
function Obter-PortaLivre {
    param([int]$Inicio)
    for ($p = $Inicio; $p -lt $Inicio + 20; $p++) {
        if (-not (Porta-EmUso $p)) { return $p }
    }
    throw "Nenhuma porta livre encontrada entre $Inicio e $($Inicio + 19)."
}

function Montar-UrlBanco {
    param([string]$HostBanco, [int]$Porta, [string]$Banco)
    return "jdbc:postgresql://${HostBanco}:$Porta/$Banco"
}
