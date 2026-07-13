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

No Bitwig: **Settings → Controllers → Add Controller → QuickRetake**. Há dois produtos:

- **QuickRetake** — use este por padrão. Não declara porta MIDI nenhuma, então ativa imediatamente (o Bitwig se recusa a ativar controllers com portas declaradas e não atribuídas).
- **QuickRetake + MIDI Trigger** — só se você for usar pedal/botão MIDI. Exige atribuir a porta de entrada para ativar.

Ao ativar, aparece o popup "QuickRetake loaded".

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
| Gesture | Late undo (triple tap outside recording) | OFF | Perdeu a janela do duplo toque e o take ruim ficou lá? **Três** toques rápidos de play/stop (fora de gravação) = para o transporte e desfaz o último passo (o take), sem precisar clicar/apagar. |
| Scope | Arranger / Launcher retake | ON/ON | Habilita cada modo independentemente. |
| Scope | Minimum recorded length | 0 beats | Gesto é ignorado se o take foi mais curto que isso (anti-falso-positivo). |
| MIDI Trigger | Trigger type / number / channel | Off | Nota ou CC de um pedal/botão: **um toque** = para + retake, ideal com as mãos no instrumento. |
| Advanced | Step delay | 100 ms | Espaçamento entre os passos (stop→undo→jump→record); aumente em projetos muito grandes. |
| Advanced | Bypass launch quantization on retake | OFF | No launcher, regrava imediatamente em vez de esperar o quantize. |
| Advanced | Show notifications | ON | Popup a cada retake. |
| Advanced | Keep discarded takes (launcher) | OFF | **Só funciona no Clip Launcher** (a API não acessa clipes do arranger). Em vez de apagar, o take fica no slot e a regravação vai para o primeiro slot **abaixo** na mesma pista. Sem slot vazio → sobrescreve com aviso. Teste: ligue a opção, grave num slot do launcher, espaço-espaço → o take antigo permanece e o slot de baixo começa a gravar. |

## MIDI Comping (takes por lanes)

O Bitwig só tem comping para áudio. O QuickRetake implementa o equivalente MIDI possível via API: **cada volta do loop grava numa pista ("lane") diferente**, rotacionando o record-arm automaticamente na virada do loop. No fim você tem N takes completos, um por lane, e audiciona com solo exclusivo.

**Setup (uma vez por instrumento):**

1. Crie a pista do instrumento normalmente (com o synth/sampler).
2. Crie 3–8 pistas de **notas vazias** (sem instrumento) — os seus lanes. Dica: agrupe-as com a pista do instrumento para organização.
3. Em cada lane, rotear a **saída de notas** para a pista do instrumento (no Inspector/roteamento da pista: Note Out → pista do instrumento). Assim qualquer lane toca o mesmo som.

**Gravando takes:**

1. Selecione a região com o **loop do arranger** (as chaves de ciclo) — é ela que define o take, como você pediu.
2. **Arme (record-arm) todos os lanes** que quiser usar.
3. No painel **Studio I/O** (borda direita do Bitwig), na seção do QuickRetake, clique **"Start"** (MIDI Comping). A extensão: liga o loop, pula para o início dele, deixa só o lane 1 armado e começa a gravar.
4. Toque. A cada volta do loop, o arm pula sozinho para o próximo lane — cada passada vira um take na sua própria pista (popup mostra "Comping: lane 2/4 …").
5. Deu o número de takes que queria? **"Stop"**. (Se passar do último lane, ele volta ao 1º e a passada faz overdub por cima — o popup avisa.)
6. Audição: **"Audition next lane"** faz solo exclusivo de um lane por vez; **"Clear solos"** limpa. Montar o take final (recortar/colar os melhores trechos entre lanes) é manual no editor — a API não move notas entre clipes do arranger.

Durante o comping, os gestos de retake ficam suspensos (senão um undo engoliria todos os lanes de uma vez — a gravação em loop contínua é um passo só de undo).

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
