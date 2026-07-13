# QuickRetake

Retake instantâneo de MIDI no Bitwig Studio: errou o take, **aperta espaço duas vezes rápido** — a extensão descarta o que foi gravado, volta o playhead ao início do take e já regrava.

O Bitwig tem comping para áudio, mas nada equivalente para MIDI (pedido recorrente da comunidade). Esta controller extension resolve isso 100% dentro do Bitwig, sem software externo.

## Requisitos

- Bitwig Studio 6.x
- Para compilar: JDK 21 + Maven

## Instalação

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn package
```

O build copia `QuickRetake.bwextension` direto para `~/Documents/Bitwig Studio/Extensions/` (o Bitwig recarrega sozinho ao trocar o arquivo).

No Bitwig: **Settings → Controllers → Add Controller → QuickRetake → QuickRetake**. Pode deixar a porta MIDI sem atribuir — o gesto de transporte funciona sem MIDI. Ao ativar, aparece o popup "QuickRetake loaded".

## Como usar

1. Grave normalmente (arme a pista, `R`/espaço, toque).
2. Errou? **Espaço, espaço** (dentro de 400 ms). A extensão:
   - **Arranger:** para o transporte, desfaz o take (1 undo), volta ao ponto onde o play começou e regrava (seu count-in normal se aplica).
   - **Launcher:** apaga o(s) clipe(s) que estava(m) gravando (1 passo de undo nomeado) e dispara a gravação do slot de novo.
3. Take bom? Pare normal com **um** espaço (ou espere >400 ms antes de dar play de novo).

Como a extensão não enxerga o teclado (limitação da API do Bitwig), o "duplo espaço" é inferido do transporte: *parou enquanto gravava → tocou de novo dentro da janela*. Qualquer coisa que pare/toque o transporte conta (botão de controladora, clique no play etc.).

## Configurações (Settings → Controllers → QuickRetake)

| Categoria | Opção | Default | Descrição |
|---|---|---|---|
| Gesture | Gesture | Stop then Play | `Stop, Play, Stop (strict)` = modo triplo: 2º toque continua tocando (audição); só o 3º toque dentro da janela dispara o retake. Zero falso positivo. |
| Gesture | Tap window | 400 ms | Janela do duplo/triplo toque (150–1000 ms). |
| Gesture | Suppress re-record during tap window | ON | Evita que o 2º espaço inicie uma gravação-lixo no arranger antes do retake agir. |
| Scope | Arranger / Launcher retake | ON/ON | Habilita cada modo independentemente. |
| Scope | Minimum recorded length | 0 beats | Gesto é ignorado se o take foi mais curto que isso (anti-falso-positivo). |
| MIDI Trigger | Trigger type / number / channel | Off | Nota ou CC de um pedal/botão: **um toque** = para + retake, ideal com as mãos no instrumento. |
| Advanced | Step delay | 100 ms | Espaçamento entre os passos (stop→undo→jump→record); aumente em projetos muito grandes. |
| Advanced | Bypass launch quantization on retake | OFF | No launcher, regrava imediatamente em vez de esperar o quantize. |
| Advanced | Show notifications | ON | Popup a cada retake. |
| Advanced | Keep discarded takes (launcher) | OFF | Em vez de apagar, o take fica no slot e a regravação vai para o primeiro slot vazio abaixo (pseudo-comping). Sem slot vazio → sobrescreve com aviso. |

## Proteções embutidas

- Parar/tocar playback **sem gravação** nunca dispara nada (o gesto só arma vindo de uma gravação).
- **Count-in:** retake durante o pre-roll não consome undo (nada foi gravado ainda) — só volta e regrava.
- Retake acidental? O descarte é **um** Ctrl+Z (arranger: o undo do take; launcher: passo "QuickRetake discard").
- Gravação simultânea arranger + launcher: não suportado — a extensão avisa e não destrói nada (o undo engoliria os dois).

## Limitações conhecidas

- A API do Bitwig não enumera nem deleta clipes do arranger; por isso o descarte no arranger usa undo. Guardar takes (keep-takes) só existe no launcher.
- Slots além da janela de 32 pistas × 32 cenas não são detectados.
- "Parei a gravação e dei play imediatamente para ouvir" dentro da janela é indistinguível do gesto no modo duplo — use o modo estrito (triplo) ou aumente/reduza a janela se incomodar.

## Roteiro de teste manual

1. **Carga:** extensão ativa sem erros no console; popup "QuickRetake loaded"; rebuild → hot reload.
2. **Arranger:** grave 3 notas → espaço-espaço → notas somem, playhead volta, regravando; 1 Ctrl+Z desfaz só o take novo. Repita com count-in 2 compassos (retake no pre-roll não consome undo — confira o rótulo de Undo no menu Edit). Stop e play >1 s depois → nada acontece. Play/stop sem gravar → nada.
3. **Launcher:** grave num slot → espaço-espaço → slot regravando; Ctrl+Z restaura o take antigo. Com launch quantization 1 bar → início enfileirado. Arranger armado + slot gravando juntos → popup, nada apagado.
4. **Overdub (risco conhecido):** grave por cima de clipe existente com Arranger Overdub ON e faça retake — confira se o undo devolve o clipe original intacto.
5. **Gesto/MIDI:** janela 150 ms vs 1000 ms; modo triplo (stop-play sem 3º toque = segue tocando); pedal em Note 64 → um toque = retake.
6. **Keep takes:** ligue a opção, 3 retakes no launcher → takes preservados nos slots de cima, gravação desce para o próximo vazio.

Logs de depuração: console do controller script (ícone ao lado do QuickRetake em Settings → Controllers), prefixo `[QR]`.
