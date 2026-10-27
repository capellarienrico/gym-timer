# Gym Timer ⏱️ (Timer de Academia com Janela Flutuante)

Aplicativo Android nativo (Kotlin) desenvolvido para descanso entre séries na academia.

---

## 🚀 Principais Recursos

1. **Tempos Predefinidos Rápidos**:
   - `45s`
   - `1m` (60s)
   - `1m 30s` (90s)
   - Ajustes rápidos de `+15s`, `-15s` e `+30s` para personalizar qualquer tempo.

2. **Vibração na Intensidade Máxima (Sem Som)**:
   - Utiliza a API `VibrationEffect` com amplitude máxima (`255`) em rajadas fortes para ser sentido claramente no bolso.
   - **Zero bipes, músicas ou toques sonoros** — não interfere na sua música (Spotify, Deezer, YouTube Music).

3. **Janela Flutuante Arrastável (Overlay)**:
   - Ao minimizar o app ou trocar de tela, uma janelinha compacta aparece sobre os outros aplicativos.
   - **Arrastável**: você pode mover para qualquer canto da tela.
   - **Controles**: exibe a contagem regressiva, botão de pausar/retomar e botão de fechar/parar.
   - Ao tocar no timer da janelinha, o app principal reabre em tela cheia.

4. **Foreground Service Persistente**:
   - Mantém o timer rodando com precisão mesmo se a tela desligar ou o celular for bloqueado.

---

## 📦 Como Gerar o Arquivo `.apk`

Você tem **3 opções simples** para compilar:

### Opção 1: Compilação Automática na Nuvem (Recomendada - Sem instalar nada)
Este projeto já vem com o arquivo `.github/workflows/build-apk.yml` pronto:
1. Crie um repositório no seu GitHub (pode ser privado).
2. Envie esta pasta para lá:
   ```bash
   git remote add origin https://github.com/SEU_USUARIO/gym-timer.git
   git branch -M main
   git push -u origin main
   ```
3. O GitHub Actions iniciará a compilação automaticamente. Em cerca de 1 a 2 minutos, o APK estará pronto para download na aba **Actions > Build Gym Timer APK > Artifacts** (`GymTimer-Debug-APK`).
4. Baixe no celular e instale!

---

### Opção 2: Android Studio (Se já tiver ou for usar)
1. Abra o **Android Studio**.
2. Clique em **Open** e selecione a pasta: `C:\Users\User\GymTimer`.
3. Aguarde o Gradle sincronizar e clique no menu:
   **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
4. O APK gerado estará em:
   `app\build\outputs\apk\debug\app-debug.apk`.

---

### Opção 3: Linha de Comando (Local)
Se você tiver o JDK 17 e o Android SDK configurados:
```powershell
cd C:\Users\User\GymTimer
.\gradlew assembleDebug
```
O APK gerado ficará em:
`app\build\outputs\apk\debug\app-debug.apk`.

---

## 📱 Permissões do Android
Ao abrir o app pela primeira vez:
- **Sobrepor a outros aplicativos**: O app mostrará um aviso destacado com um botão direto para permitir. Essa permissão é necessária para que a janela flutuante funcione sobre o Spotify, WhatsApp ou Instagram.
- **Notificações**: Necessária no Android 13+ para o timer continuar rodando em segundo plano.
- **Vibração**: Concedida automaticamente na instalação.
