import os
import logging
from datetime import datetime
from telegram import Update, ReplyKeyboardMarkup
from telegram.ext import Application, CommandHandler, MessageHandler, filters, ContextTypes

logging.basicConfig(level=logging.INFO)

TOKEN = os.environ.get("TOKEN")
ADMIN_ID = int(os.environ.get("ADMIN_ID"))

records = {}

main_keyboard = ReplyKeyboardMarkup(
    [["✅ Пришёл", "🏠 Ушёл"], ["📊 Статистика"]],
    resize_keyboard=True
)

async def start(update: Update, context: ContextTypes.DEFAULT_TYPE):
    user = update.effective_user
    await update.message.reply_text(
        f"Привет, {user.first_name}! 👋\n\nВыбери действие:",
        reply_markup=main_keyboard
    )

async def handle_message(update: Update, context: ContextTypes.DEFAULT_TYPE):
    user = update.effective_user
    text = update.message.text
    now = datetime.now().strftime("%H:%M %d.%m.%Y")
    user_id = user.id
    name = user.full_name

    if text == "✅ Пришёл":
        if user_id not in records:
            records[user_id] = {}
        records[user_id]["arrived"] = now
        records[user_id]["name"] = name
        await update.message.reply_text(
            f"✅ Отмечено! Ты пришёл в {now}",
            reply_markup=main_keyboard
        )
        await context.bot.send_message(
            chat_id=ADMIN_ID,
            text=f"📥 {name} пришёл в {now}"
        )

    elif text == "🏠 Ушёл":
        if user_id not in records:
            records[user_id] = {}
        records[user_id]["left"] = now
        records[user_id]["name"] = name
        await update.message.reply_text(
            f"🏠 Отмечено! Ты ушёл в {now}",
            reply_markup=main_keyboard
        )
        await context.bot.send_message(
            chat_id=ADMIN_ID,
            text=f"📤 {name} ушёл в {now}"
        )

    elif text == "📊 Статистика":
        if user.id == ADMIN_ID:
            if not records:
                await update.message.reply_text("Пока никто не отметился")
                return
            msg = "📊 Статистика за сегодня:\n\n"
            for uid, data in records.items():
                n = data.get("name", "Неизвестный")
                arrived = data.get("arrived", "—")
                left = data.get("left", "—")
                msg += f"👤 {n}\n  Пришёл: {arrived}\n  Ушёл: {left}\n\n"
            await update.message.reply_text(msg)
        else:
            if user_id in records:
                data = records[user_id]
                arrived = data.get("arrived", "—")
                left = data.get("left", "—")
                await update.message.reply_text(
                    f"📊 Твоя статистика:\n\nПришёл: {arrived}\nУшёл: {left}"
                )
            else:
                await update.message.reply_text("У тебя пока нет отметок сегодня")

def main():
    app = Application.builder().token(TOKEN).build()
    app.add_handler(CommandHandler("start", start))
    app.add_handler(MessageHandler(filters.TEXT & ~filters.COMMAND, handle_message))
    app.run_polling()

if __name__ == "__main__":
    main()
