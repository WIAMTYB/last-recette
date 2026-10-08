# API Token Admin

Outil CLI pour generer du materiel de token `X-Api-Key` **sans connexion a la base**.

Il est identique a celui de `api-titres-selfcare` car les deux services partagent les memes tables Oracle :

- `API_CLIENT_TOKEN`
- `API_CLIENT_TOKEN_AUTHORIZATION`

Un token cree une fois peut etre autorise sur api-titres, epargne-programmee, ou les deux.

## Usage

```powershell
.\api-token-admin.exe generate `
  --client-code CLIENT_A `
  --created-by security-team-user `
  --description "CLIENT_A production token" `
  --expires-at "2027-05-19 00:00:00"
```

Sortie :

- `Token ID` : valeur pour `API_CLIENT_TOKEN.TOKEN_ID`
- `Secret` : a remettre au client (hors systeme)
- `Secret Hash` : valeur pour `API_CLIENT_TOKEN.SECRET_HASH`
- `SQL` : `INSERT INTO API_CLIENT_TOKEN` pret a executer

Le client appelle ensuite :

```text
X-Api-Key: <Token ID>.<Secret>
```

Le secret en clair n'est affiche qu'une seule fois.

## Exemple d'autorisations Epargne

Apres l'insert du token, ajouter les autorisations pour les routes Epargne :

```sql
INSERT INTO API_CLIENT_TOKEN_AUTHORIZATION (
  TOKEN_ID,
  PATH_PATTERN,
  HTTP_METHOD,
  CREATED_BY
) VALUES (
  'ak_generatedTokenId',
  '/api/plans/**',
  NULL,
  'security-team-user'
);

INSERT INTO API_CLIENT_TOKEN_AUTHORIZATION (
  TOKEN_ID,
  PATH_PATTERN,
  HTTP_METHOD,
  CREATED_BY
) VALUES (
  'ak_generatedTokenId',
  '/actuator/prometheus',
  'GET',
  'security-team-user'
);
```

Pour un token partage entre les deux APIs, ajouter aussi les paths api-titres (`/tradableasset/**`, etc.).

## Build

Jar :

```powershell
mvn -f scripts\api-token-admin\pom.xml clean package
```

Executable Windows natif (GraalVM Native Image + MSVC) :

```powershell
mvn -f scripts\api-token-admin\pom.xml -Pnative-windows clean package
```

L'executable est genere sous `scripts\api-token-admin\target\`.
