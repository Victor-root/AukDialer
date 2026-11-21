# 🤝 Contributing to AukDialer

Thanks for your interest in contributing to **AukDialer**! 🚀

---

## 🐛 Reporting a bug

First check that it hasn't been reported already, then give as many details as possible:

* 📱 Phone, Android version, manufacturer skin or ROM, and AukDialer version
* 📶 Number of SIM cards, carrier and country
* 🔁 Steps to reproduce, what you expected and what actually happened
* 🖼️ Screenshots, and Logcat logs if the problem keeps happening
* 🔎 Depending on the case: Bluetooth or car kit, Shizuku version for call recording, the account the contact is saved in
* 📬 Visual voicemail: attach the diagnostics (Settings → Voicemail → **Copy diagnostics**), it's what makes it possible to support your carrier. It contains no password and none of your callers' numbers.

> [!WARNING]
> A Phone app shows personal data. Before posting a screenshot or logs, **hide your contacts' numbers, names and messages**. **Screenshot mode** (Settings) does it for you.

---

## 💡 Suggesting a feature

Ideas are welcome! Open an issue describing **the need**: what you want to do and what stops you today.

---

## 🔧 Pull requests

Pull requests are welcome! 🎉 One pull request = one clear change. Several small ones are better than a big one.

* 📞 Never break the essentials: placing, receiving and hanging up a call
* 🎨 Any visual addition must blend into the existing design: something out of line will get a request for changes
* 🌐 Every new text goes in English in `values/strings.xml`. If your native language is another one, add it there too: a native translation is the most reliable (see [TRANSLATING.md](TRANSLATING.md))

**📝 The description** must be understandable by any reader, even without any knowledge of code: the problem, what changes for the user, and how it was tested. **Code is not discussed there**: it is looked at during the review. The goal is that anyone browsing the history of pull requests understands what each one is about.

---

## 🤖 With the help of an AI

Using an AI (Claude, ChatGPT, Copilot...) to contribute is **welcome**, and it can write the description too, as long as it follows the rule above. Simply say that an AI was used.

What is not welcome is **AI slop**: a generated change sent without being really tested. What matters above all is **testing**.

* 📱 Test on a real phone with a SIM card: "it compiles" or "the AI says it works" are not tests
* 🔁 Test the case you changed, but also the essentials around it: incoming call, outgoing call, hanging up from both sides
* ⏳ Take your time: telephony on Android is finicky, and a bug can take a while to show up
* 📋 In the description, say exactly what you did (phone, Android version, carrier, scenarios tried), not just "it works"
* 🎯 Keep the change focused on the problem: no refactoring or "improvements" nobody asked for

A pull request without serious testing will be **closed without review**.

---

## 🙏 Thanks

Every bug report, idea, translation or fix helps the project, even the smallest. 🚀
