# lead-capture-api

API de captação de leads para várias empresas, feita em Java com Spring Boot e PostgreSQL.

É uma versão pequena e fictícia de um problema que resolvi num SaaS de marketing: um formulário ou integração manda o lead, a API confere de qual empresa ele é, coloca numa fila e um worker grava depois. Nenhum código daquele projeto está aqui, escrevi tudo de novo para mostrar a ideia.

## Como o lead anda

```
formulário ou integração
  POST /capture  (cabeçalho X-Api-Key)
    confere a chave e descobre a empresa
    valida os campos
    grava em ingestion_events com status pending
    responde 202 "queued"
worker (a cada 1 segundo)
  pega um lote com FOR UPDATE SKIP LOCKED
  grava o lead e marca o evento como done
  se falhar: tenta de novo com espera crescente; depois de 5 vezes, status dead
```

Algumas decisões:

- **202 e não 201.** A API não promete que o lead foi criado, só que entrou na fila. Assim um pico de envios não derruba o banco nem deixa o formulário esperando.
- **Sem duplicar.** Cada empresa manda um `eventId`. Se o mesmo `eventId` chegar duas vezes (o cliente reenviou porque a rede caiu, por exemplo), a segunda vez responde `"duplicate": true` e não cria outro lead. Quem garante isso é a restrição `UNIQUE (organization_id, event_id)` no banco.
- **Dois workers não pegam o mesmo evento.** `FOR UPDATE SKIP LOCKED` faz cada worker pular as linhas que outro já travou. Se um worker cair no meio, o evento fica em `processing` e volta para a fila depois de 5 minutos.
- **Lead e status juntos.** Gravar o lead e marcar o evento como `done` acontecem na mesma transação.
- **A chave não fica no banco.** A tabela guarda só o SHA-256 da chave de API.
- **Chave antes de validação.** Sem chave válida a resposta é 401, antes de olhar o conteúdo.
- **Cada empresa vê só os seus leads.** `GET /leads` filtra pela empresa dona da chave.

## Tecnologias

Java 21, Spring Boot 3.3, Spring JDBC, Flyway, PostgreSQL 16, JUnit 5, Testcontainers e GitHub Actions.

## Rodando

Precisa de Java 21, Maven e Docker.

```bash
docker compose up -d        # sobe o PostgreSQL
mvn spring-boot:run         # sobe a API na porta 8080
```

O Flyway cria as tabelas e duas empresas de teste. As chaves são `demo-key-org-a` e `demo-key-org-b`.

Mandando um lead:

```bash
curl -i -X POST http://localhost:8080/capture \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: demo-key-org-a" \
  -d '{"eventId": "form-001", "email": "ana@exemplo.com", "name": "Ana", "source": "site"}'
```

Resposta:

```
HTTP/1.1 202
{"status":"queued","duplicate":false}
```

Um segundo depois o worker já gravou. Para ver:

```bash
curl http://localhost:8080/leads -H "X-Api-Key: demo-key-org-a"
```

Com a chave `demo-key-org-b` a lista vem vazia.

## Testes

```bash
mvn verify
```

`CaptureFlowTest` sobe um PostgreSQL de verdade com Testcontainers (por isso precisa do Docker) e testa:

- o lead entra na fila e só vira lead depois do worker;
- o mesmo `eventId` não duplica;
- chave errada recebe 401 e não grava nada;
- e-mail inválido recebe 400 dizendo qual campo está errado;
- uma empresa não vê os leads da outra;
- evento quebrado tenta de novo e depois vai para `dead`.

`BackoffTest` confere o tempo de espera entre tentativas.

O GitHub Actions roda `mvn verify` a cada push.

## Estrutura

```
src/main/java/com/exemplo/leads
  capture/   endpoint, chave de API, validação
  queue/     fila no PostgreSQL e backoff
  worker/    worker agendado e gravação do lead
src/main/resources/db/migration   tabelas (Flyway)
```
