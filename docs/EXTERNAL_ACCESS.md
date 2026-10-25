# External access

An outside AI — a claude.ai chat first — reads and writes in the app with the built-in AI's commands, through an MCP server that runs in the app, on the phone, where the data lives. The server is generic: Claude is one client among others, and nothing in the app names it. What the commands do once received is in `docs/AI.md` ("External access (MCP server)", "Levels"); this doc says how a request reaches the phone and who may send it.

## Two ways in

A remote client cannot reach a phone: claude.ai calls from Anthropic's servers and needs a public HTTPS address, and a phone on 4G receives no connection. The setting `access_mode` of the external access chooses the way (`TAILSCALE` for a new install, `RELAY` after the migration when a relay was set), shown everywhere, never switched by itself: without the native library (any ABI but arm64) opening in Tailscale fails saying to choose the relay.

- **Tailscale**: a Tailscale node embedded in the app (`tsnet`, Go) opens a Funnel at `https://treelune.<tailnet>.ts.net/mcp`; the HTTPS ends on the phone, with its own Let's Encrypt certificate. It needs a free Tailscale account. No system VPN, no port opened on the phone's network: the node keeps an outgoing connection to Tailscale. The node also answers from the person's own tailnet, where the address leads to the phone directly and not to the Funnel: the authorisation page, opened in a browser on a computer with Tailscale, comes that way.
- **Relay**: a plain pipe on a host of one's own (the project `relay`, `/home/simon/Projets/OUTILS/relay`), which sees the data in clear (its HTTPS ends there) and keeps nothing. Its address and secret are a case of the mode (`SettingNode.Variant`), erased on switching to Tailscale. It is the way out if Tailscale changes Funnel, and the only one that depends on no third service.

A client keeps the address it was added with: changing mode means adding it again with the new address. The OAuth is served by the app, so an authorised client stays one from a mode to the other.

## The relay's contract

What the relay and the app owe each other, so that each is written without the other. `<base>` is the relay's public address; the Go node speaks the same contract, in the same JSON, so that `RelayLoop`, `McpHttp`, the OAuth and the closing after 30 minutes see no difference (`RelayTransport`, `FunnelTransport`).

- **Client side**, any request to `<base>/…` but `<base>/_relay/…` is queued as it is: a random id, the method, the path under `<base>`, the query string, the headers (without the transport's: `Host`, `Connection`, `Content-Length`…), the body. The relay holds it open until the app's answer, `REPLY_TIMEOUT` (25 s) at most, then answers 503 and erases it.
- **App side**, every call to `<base>/_relay/…` bears `Authorization: Bearer <relay secret>`; without it, 404. `GET <base>/_relay/next?wait=<s>` returns the oldest request waiting, removed from the queue, or 204 after `wait` seconds (`RelayLoop.WAIT_SECONDS` = 20, under the 120 s the host holds a request); `POST <base>/_relay/reply/<id>` gives the answer, 404 if the client no longer waits.
- **The JSON**: a request `{"id", "method", "path", "query", "headers": {name: value}, "body"}`, an answer `{"status", "headers", "body"}`, the bodies in base64.
- **Nothing stays**, a body of 1 MB at most each way (413), a rate per minute and per address, a bounded queue (503 when full). **No stream**: the app answers in one block as `application/json` (MCP allows it) and refuses the `GET` of an SSE stream with 405.

## The access open

- Opened by hand, in the settings or from a quick settings tile; a foreground service whose notification says the state and the last call, with Close. Closed by itself after 30 minutes without a call, each call restarting the delay. Closed, nothing passes, valid token or not.
- Open, the app keeps one request waiting at the relay (or the node), and starts another at once.

## The authorisation

The app is its own OAuth server (metadata, dynamic client registration, authorisation page, token: `OAuthServer`), served through the way in. Once: the access open, the user adds the address as a connector in the client; the client registers, its browser opens the authorisation page, which shows a 4-digit code and waits; the phone notifies the request, its window shows the name the client gives itself and the domain the answer goes to, both "declared by the client", a field for the code, Authorise and Refuse; the right code authorises.

- Three wrong codes cancel the request; a request expires in 5 minutes; one at a time. Any https return address (or one on the machine itself) is accepted and shown: the security holds by the code, which only whoever sees the page has, and by PKCE.
- An access token lasts an hour, renewed by the client; a refresh token is revoked after 90 days unused and replaced at each renewal, the former staying valid until the new pair is first used (`StoredToken.replaces`), so that an answer lost on the way does not leave the client unable to renew. The clients authorised are listed and revoked in the settings.
- Each request received goes to the app's log by its method, path and status, an OAuth error with its reason, never what follows `?`, a content or a token; a registration names the client created, a token request says whether it is granted and why.

## What the client sees

- One MCP tool per command of the catalogue (`AICommands`: name, parameters' schema, description, read or write, `readOnlyHint`), and `upload_address`, the connector's own (« Sending a file »); the client sets tool by tool what it allows at once or asks.
- `app_context`, in read: the app's notions, L2 and L3, and a context token signed by the app, valid 24 hours (`ContextTokens`). Every other tool requires it, refused otherwise ("call `app_context` first"): the model can do nothing before reading the context, and a conversation taken up after 24 hours reads it again. Nothing depends on the MCP session, which is not a conversation. No server `instructions` nor anything sent unasked: only the tools and their answers surely reach the model.
- Each tool's answer bears the date and time, in the app's zone; the parameters read as the built-in AI's (ISO, relative periods), by the same path.

## Sending a file

The bytes of a file never go through the AI, which writes a tool's parameters and could not hold a file of some MB. The connector's own tool `upload_address` (`{kind, name}`, which the app's AI has no use for) hands out an address `<base>/upload/<token>`, valid once and 5 minutes (`McpUploads`); the client sends the file there with a PUT, beside the model (`curl -X PUT --data-binary @induction.mp3 <upload_url>`, from Claude Code: a conversation of claude.ai has neither the computer's files nor the way to send them). The address is its own authorisation, spent whatever comes of it. The app checks the file as one picked on the phone (`FilePreparation`: its kind, its format, its size, that Android plays it), sets it aside in the pending folder, and answers `file_id`, `kind`, `format`, `size_bytes` and what goes with its kind (a medium's `duration` in ISO 8601); the AI writes that id in an entry's FILE field, and the file becomes the entry's (`docs/DATA.md`, « FILE »). A file sent that no write takes is erased at the next startup.

- **Through the Tailscale node**, every other request's body is held to 4 MiB in memory; a PUT to an upload address the node was told of (`expectUpload`, JNI) is written straight into a file, through a part renamed, up to its kind's size (an audio 50 MB, a video 200 MB, an image or a text as a video), and Kotlin receives its path (`file` in the request's JSON) instead of its bytes.
- **Through the relay**, a body holds 1 MB at most, refused beyond by the relay itself: a larger file goes through the Tailscale node.

## The Tailscale node

- **Set up reactively**: no list of steps shown ahead; the screen says what the node answers, one missing step at a time, the node trying again every 5 s, the access opening as soon as the step is done. Not registered: "Connect the app to Tailscale" opens the login URL (`login.tailscale.com/a/…`), where Tailscale also creates the account. Funnel off in the account: the node asks Tailscale for its activation page (`QueryFeature("funnel")`), one click enabling HTTPS and the right. No page offered (a member who cannot change the account): the steps by hand — HTTPS through the console's DNS page, and the block `nodeAttrs` to copy into the access policy's JSON editor (`login.tailscale.com/admin/acls/file`). Ready: the address `…/mcp`.
- **The waits**, measured on the prototype (Samsung S10, Android 16): until Tailscale publishes the address in the public DNS, 12 s to more than 11 minutes, which the screen says, since a "no such name" stays 5 minutes in the DNS servers that got it (the node asks the servers of `ts.net` themselves every 10 s); the first call after the registration waits for the certificate, about 40 s; the next calls take 0.5 to 1.3 s, on Wi-Fi as on 4G. The certificates are published in the public Certificate Transparency logs: unknown robots visit the address within seconds.
- **Its life**: it runs only while the access is open (started in about 1 s once registered). Its identity lives in `files/tailscale/`, never in the database, so never in Treelune's export, and is kept out of Android's backup and transfer (`backup_rules.xml`, `data_extraction_rules.xml`), which would copy it to another phone — `files/mcp/` too. Its name is `treelune`, Tailscale adding a suffix when taken (`CertDomains`). "Disconnect from Tailscale" removes the node from the account and erases its folder. Android's network changes are passed to it from Kotlin (`ConnectivityManager`).
- **The Go / Kotlin boundary**: JNI written by hand in cgo, no gomobile; `start(folder, name)`, `state()` (to register and its URL, HTTPS missing, Funnel right missing, ready and its address, failure and its message), `stop()`, `logout()`, `networkChanged(interface)`, and `expectUpload(token, path, max, seconds)` for an upload address, `next(wait)` / `reply(id, json)` for the relay's contract.
- **What Android asks of `tsnet`**: the certificate route `cert/` of its local API is excluded from Android builds, every connection failing with 404 — `android` is removed from the conditions of `ipn/localapi/cert.go` and `disabled_stubs.go` in the vendored copy (`scripts/vendor_go.py`, checked at each `./run test`) while Tailscale's PR #21720 is not merged; Android refuses netlink to apps, so the app registers its own interface list (`netmon.RegisterInterfaceGetter`, a synthetic interface bearing the source address the kernel gives an unsent UDP socket); an app has neither `HOME`, `XDG_CACHE_HOME` nor `TMPDIR`, set into the app's files before the start. `envknob.SetNoLogsNoSupport()` stops sending logs to `log.tailscale.com`; a Go panic, which Android shows nowhere, is written to a file (`debug.SetCrashOutput`).
- **Logs**: the messages for the user (login URL, Funnel open, errors) go to the app's log as "External access:", INFO; Tailscale's technical log to `files/tailscale/`, two rotating files of 1 MB, never sent.
- **Build**: the module in `app/src/main/go/`, its dependencies vendored and committed (`docs/reference.md`); `libtreelune_funnel.so` built for arm64 by the NDK in a Gradle task before assembling, into `build/`; reproducible (Go's `toolchain` fixed in `go.mod`, `-trimpath -buildvcs=false`, empty build id, stripped). The library stays uncompressed in the APK: the release goes from about 9 to 31 MB (computed).

## To watch

- Go code receives directly what comes from the internet, inside the app: `tsnet`'s versions are followed as a security dependency.
- Deep sleep is not measured beyond 7 minutes screen off; the access closes by itself after 30 minutes without a call.
- Once, right after a registration in the app, the node stayed 22 minutes without reaching Tailscale's relays nor publishing its address, until a restart; not reproduced, not explained. The network changes now passed from Kotlin were the lead.
