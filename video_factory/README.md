# Free video factory

Prototype branch, isolated from attendance-bot main.

Free GitHub Actions demo generates a 20-second animated MP4 using FFmpeg. The workflow is **manual only** and requires merging to default branch or a dedicated repo before Actions dispatch is available. It does NOT generate Descript-quality narration, does NOT auto-upload to YouTube/VK/Telegram and does NOT incur paid APIs.

For fuller prototype including optional voice/music files and Telegram queue publisher, download the ZIP from the ChatGPT conversation.

Next steps: separate repository, configure bot token/chat ID as GitHub secrets, implement persistent deduplication and verify posting; add YouTube OAuth and VK publishing. Never commit secrets.
