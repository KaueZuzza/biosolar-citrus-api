<#
.SYNOPSIS
  Registra no pgAdmin 4 o MESMO PostgreSQL/banco usado pela API do BioSolar Citrus.

.DESCRIPTION
  - Le a conexao do .env (BIOSOLAR_DB_URL / BIOSOLAR_DB_USUARIO): nao cria outro banco.
  - Localiza o pgAdmin 4 instalado e importa o servidor "BioSolar Citrus" com o proprio
    utilitario do pgAdmin (setup.py load-servers), depois de copiar a configuracao atual
    (pgadmin4.db) como backup. Se o servidor ja estiver registrado, nada e alterado.
  - A senha nao e gravada: o pgAdmin pede na primeira conexao (marque "Salvar senha").

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\pgadmin.ps1
#>
param(
    [string]$Ferramentas = (Join-Path $env:LOCALAPPDATA 'BioSolarDev')
)

$ErrorActionPreference = 'Stop'
$raiz = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'ambiente.ps1')
Importar-DotEnv (Join-Path $raiz '.env')

$url = Ler-UrlBanco $env:BIOSOLAR_DB_URL
if (-not $url) { throw "BIOSOLAR_DB_URL invalida: $env:BIOSOLAR_DB_URL" }
$usuario = if ($env:BIOSOLAR_DB_USUARIO) { $env:BIOSOLAR_DB_USUARIO } else { 'biosolar' }

# O pgAdmin precisa do servidor rodando para conectar: confere a porta real do PostgreSQL portatil
if (Eh-HostLocal $url.Host) {
    $portaAtiva = Obter-PortaClusterPortatil -PgBin (Join-Path $Ferramentas 'pgsql\bin') -PgData (Join-Path $Ferramentas 'pgdata')
    if ($portaAtiva -and $portaAtiva -ne $url.Porta) {
        Write-Host "[i] O PostgreSQL do BioSolar esta na porta $portaAtiva (o .env dizia $($url.Porta)): usando $portaAtiva."
        $url.Porta = $portaAtiva
    }
}

$nomeServidor = 'BioSolar Citrus'
$servidor = [ordered]@{
    Name                 = $nomeServidor
    Group                = 'BioSolar Citrus'
    Host                 = $url.Host
    Port                 = [int]$url.Porta
    MaintenanceDB        = $url.Banco
    Username             = $usuario
    Comment              = "Banco usado pela API do BioSolar Citrus ($($url.Host):$($url.Porta)/$($url.Banco)). Mesmo banco da aplicacao."
    ConnectionParameters = [ordered]@{ sslmode = 'prefer'; connect_timeout = 10 }
}
$json = [ordered]@{ Servers = [ordered]@{ '1' = $servidor } } | ConvertTo-Json -Depth 5
$arquivoJson = Join-Path $env:TEMP 'biosolar-pgadmin-servidor.json'
[IO.File]::WriteAllText($arquivoJson, $json, (New-Object Text.UTF8Encoding($false)))

function Mostrar-Instrucoes {
    Write-Host ''
    Write-Host '  Como ver o banco no pgAdmin 4:'
    Write-Host "   1. Abra o pgAdmin (se ja estava aberto, feche e abra de novo) > Servers > grupo 'BioSolar Citrus' > servidor '$nomeServidor'."
    Write-Host "   2. Senha do usuario '$usuario': a mesma do .env (padrao: biosolar). Marque 'Salvar senha'."
    Write-Host "   3. Databases > $($url.Banco) > Schemas > public > Tables: talhao, reservatorio, estado_simulacao,"
    Write-Host '      evento, leitura_telemetria, leitura_talhao. Views: vw_aspersores, vw_leituras_talhao.'
    Write-Host "   4. Relacionamentos: clique com o botao direito em '$($url.Banco)' > ERD For Database."
    Write-Host '   5. Dados: botao direito na tabela > View/Edit Data > All Rows (F5 atualiza).'
    Write-Host '  A API grava o estado a cada segundo; mudancas de CADASTRO feitas no pgAdmin chegam a automacao em ate 5 s.'
    Write-Host ''
}

# Localiza o pgAdmin 4 (instalado junto com o PostgreSQL ou separado)
$candidatos = @()
$candidatos += Get-ChildItem 'C:\Program Files\PostgreSQL\*\pgAdmin 4' -Directory -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName }
$candidatos += @('C:\Program Files\pgAdmin 4', (Join-Path $env:LOCALAPPDATA 'Programs\pgAdmin 4'))
$pgAdmin = $candidatos | Where-Object { Test-Path (Join-Path $_ 'web\setup.py') } | Select-Object -First 1

if (-not $pgAdmin) {
    Write-Host '[i] pgAdmin 4 nao encontrado. Registre o servidor manualmente (Object > Register > Server):'
    Write-Host "    Host: $($url.Host)  Porta: $($url.Porta)  Banco: $($url.Banco)  Usuario: $usuario"
    Write-Host "    Ou importe o arquivo $arquivoJson em Tools > Import/Export Servers."
    Mostrar-Instrucoes
    exit 0
}

$python = Join-Path $pgAdmin 'python\python.exe'
$setup = Join-Path $pgAdmin 'web\setup.py'
$configPgAdmin = Join-Path $env:APPDATA 'pgAdmin\pgadmin4.db'
$usuarioPgAdmin = 'pgadmin4@pgadmin.org'   # usuario interno do pgAdmin no modo desktop
Write-Host "[ok] pgAdmin 4 encontrado em $pgAdmin"

# Ja registrado? (le a lista atual sem alterar nada)
if (Test-Path $configPgAdmin) {
    $atual = Join-Path $env:TEMP 'biosolar-pgadmin-atual.json'
    & $python $setup dump-servers $atual --user $usuarioPgAdmin --sqlite-path $configPgAdmin | Out-Null
    if (Test-Path $atual) {
        $existentes = (Get-Content $atual -Raw -Encoding UTF8 | ConvertFrom-Json).Servers.PSObject.Properties | ForEach-Object { $_.Value }
        $ja = $existentes | Where-Object { $_.Name -eq $nomeServidor } | Select-Object -First 1
        if ($ja) {
            if ([int]$ja.Port -ne [int]$url.Porta -or $ja.Host -ne $url.Host) {
                Write-Host "[!] O pgAdmin ja tem o servidor '$nomeServidor', mas em $($ja.Host):$($ja.Port)."
                Write-Host "    Ajuste em Properties > Connection para $($url.Host):$($url.Porta) (ou apague-o e rode este script de novo)."
            } else {
                Write-Host "[ok] O pgAdmin ja tem o servidor '$nomeServidor' em $($url.Host):$($url.Porta). Nada foi alterado."
            }
            Mostrar-Instrucoes
            exit 0
        }
    }
    $backup = "$configPgAdmin.antes-biosolar-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
    Copy-Item $configPgAdmin $backup
    Write-Host "[ok] Backup da configuracao do pgAdmin: $backup"
}

if (Get-Process -Name 'pgAdmin4' -ErrorAction SilentlyContinue) {
    Write-Host '[i] O pgAdmin esta aberto: depois do registro, feche e abra de novo para ver o servidor novo.'
}

& $python $setup load-servers $arquivoJson --user $usuarioPgAdmin --sqlite-path $configPgAdmin
if ($LASTEXITCODE -ne 0) { throw 'O pgAdmin nao conseguiu importar o servidor.' }
Write-Host "[ok] Servidor '$nomeServidor' registrado no pgAdmin ($($url.Host):$($url.Porta)/$($url.Banco), usuario $usuario)."
Mostrar-Instrucoes
