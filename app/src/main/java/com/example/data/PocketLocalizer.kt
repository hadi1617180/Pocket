package com.example.data

import androidx.compose.runtime.compositionLocalOf

val LocalAppLanguage = compositionLocalOf { "EN" }

object PocketLocalizer {
    @Volatile
    var currentLanguage: String = "EN"

    fun isBangla(lang: String = currentLanguage): Boolean =
        lang.trim().equals("BN", ignoreCase = true)

    fun toBanglaDigits(input: String): String {
        if (input.isEmpty()) return input
        val sb = StringBuilder(input.length)
        for (ch in input) {
            if (ch in '0'..'9') {
                sb.append(('০'.code + (ch.code - '0'.code)).toChar())
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun normalizeToAsciiDigits(input: String): String {
        if (input.isEmpty()) return input
        val sb = StringBuilder(input.length)
        for (ch in input) {
            if (ch in '০'..'৯') {
                sb.append(('0'.code + (ch.code - '০'.code)).toChar())
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun normalizeDigits(input: String): String = normalizeToAsciiDigits(input)

    fun localizeDigits(input: String, lang: String = currentLanguage): String {
        return if (isBangla(lang)) toBanglaDigits(input) else input
    }

    private val defaultWalletMap = mapOf(
        "cash" to "ক্যাশ",
        "bank" to "ব্যাংক",
        "mtb" to "এমটিবি",
        "brac" to "ব্র্যাক",
        "bkash" to "বিকাশ",
        "nagad" to "নগদ",
        "rocket" to "রকেট",
        "rapid pass" to "র‍্যাপিড পাস",
        "card" to "কার্ড",
        "savings" to "সেভিংস",
        "fund" to "ফান্ড"
    )

    private val defaultCategoryMap = mapOf(
        "salary" to "বেতন",
        "freelance" to "ফ্রিল্যান্স",
        "gift" to "উপহার",
        "gifts" to "উপহার",
        "client payment" to "ক্লায়েন্ট পেমেন্ট",
        "others" to "অন্যান্য",
        "other" to "অন্যান্য",
        "other income" to "অন্যান্য আয়",
        "bills" to "বিল",
        "shopping" to "শপিং",
        "transport" to "যাতায়াত",
        "food & dining" to "খাবার ও ডাইনিং",
        "food" to "খাবার",
        "dining" to "ডাইনিং",
        "bazar" to "বাজার",
        "groceries" to "গ্রোসারি",
        "medicine" to "ওষুধ",
        "medical" to "চিকিৎসা",
        "entertainment" to "বিনোদন",
        "donation" to "দান",
        "service charge" to "সার্ভিস চার্জ",
        "health" to "স্বাস্থ্য",
        "education" to "শিক্ষা",
        "mobile recharge" to "মোবাইল রিচার্জ",
        "fees & charges" to "ফি ও চার্জ",
        "transfer fee" to "ট্রান্সফার ফি",
        "service fee" to "সার্ভিস ফি",
        "utilities" to "ইউটিলিটি বিল",
        "business" to "ব্যবসা",
        "investments" to "বিনিয়োগ",
        "rental" to "ভাড়া",
        "wallet transfer" to "ওয়ালেট ট্রান্সফার",
        "loan lent" to "প্রদত্ত ঋণ",
        "loan borrowed" to "গৃহীত ঋণ",
        "reconciliation" to "ব্যালেন্স সমন্বয়",
        "balance reconciliation" to "ব্যালেন্স সমন্বয়",
        "general" to "সাধারণ"
    )

    fun localizeWalletName(name: String, lang: String = currentLanguage): String {
        if (!isBangla(lang)) return name
        val trimmed = name.trim()
        defaultWalletMap[trimmed.lowercase()]?.let { return it }
        return toBanglaDigits(name)
    }

    fun localizeCategoryName(name: String, lang: String = currentLanguage): String {
        if (!isBangla(lang)) return name
        val trimmed = name.trim()
        defaultCategoryMap[trimmed.lowercase()]?.let { return it }
        return toBanglaDigits(name)
    }

    private val exactPhraseMap: Map<String, String> = buildMap {
        fun putBoth(en: String, bn: String) {
            put(en.lowercase(), bn)
        }

        // Default wallets & categories
        defaultWalletMap.forEach { (k, v) -> put(k, v) }
        defaultCategoryMap.forEach { (k, v) -> put(k, v) }

        // App & Navigation
        putBoth("Pocket", "পকেট")
        putBoth("FREE", "ফ্রি")
        putBoth("Free", "ফ্রি")
        putBoth("PREMIUM", "প্রিমিয়াম")
        putBoth("Premium", "প্রিমিয়াম")
        putBoth("Balances", "ব্যালেন্স")
        putBoth("Recent", "সাম্প্রতিক")
        putBoth("Transactions", "লেনদেন")
        putBoth("Reports", "রিপোর্ট")
        putBoth("Loans", "ঋণ ও খাতা")
        putBoth("Loan Khata", "লোন খাতা")
        putBoth("Khata", "খাতা")
        putBoth("Wallets", "ওয়ালেট")
        putBoth("Wallet", "ওয়ালেট")
        putBoth("History", "ইতিহাস")
        putBoth("Transaction History", "লেনদেনের ইতিহাস")
        putBoth("Settings", "সেটিংস")
        putBoth("Categories", "ক্যাটাগরি")
        putBoth("Category", "ক্যাটাগরি")
        putBoth("Recurring", "নিয়মিত বিল")
        putBoth("Recurring Bills", "নিয়মিত বিল")
        putBoth("Recurring Bills & Subscriptions", "নিয়মিত বিল ও সাবস্ক্রিপশন")
        putBoth("Subscriptions & Bills", "সাবস্ক্রিপশন ও বিল")
        putBoth("Categories & Monthly Budgets", "ক্যাটাগরি ও মাসিক বাজেট")
        putBoth("Reconcile Wallet Balance", "ওয়ালেট ব্যালেন্স সমন্বয়")
        putBoth("Reconcile Balance", "ব্যালেন্স সমন্বয়")
        putBoth("Balance Adjustment", "ব্যালেন্স সমন্বয়")
        putBoth("Adjust Balance", "ব্যালেন্স সমন্বয়")
        putBoth("JSON Backup & Restore", "জেসন (JSON) ব্যাকআপ ও রিস্টোর")
        putBoth("Backup & Restore", "ব্যাকআপ ও রিস্টোর")
        putBoth("Backup & Export", "ব্যাকআপ ও এক্সপোর্ট")
        putBoth("Statement", "স্টেটমেন্ট")
        putBoth("Statements", "স্টেটমেন্ট")
        putBoth("Monthly Report", "মাসিক রিপোর্ট")

        // Common Actions & Buttons
        putBoth("Save", "সংরক্ষণ করুন")
        putBoth("Save Record", "রেকর্ড সংরক্ষণ করুন")
        putBoth("Save Changes", "পরিবর্তন সংরক্ষণ করুন")
        putBoth("Update", "আপডেট করুন")
        putBoth("Update Record", "রেকর্ড আপডেট করুন")
        putBoth("Cancel", "বাতিল")
        putBoth("Close", "বন্ধ করুন")
        putBoth("Delete", "মুছে ফেলুন")
        putBoth("Remove", "সরান")
        putBoth("Edit", "সম্পাদনা")
        putBoth("Add", "যোগ করুন")
        putBoth("+ Add", "+ যোগ করুন")
        putBoth("+ New", "+ নতুন")
        putBoth("New", "নতুন")
        putBoth("Duplicate", "ডুপ্লিকেট")
        putBoth("Confirm", "নিশ্চিত করুন")
        putBoth("Continue", "চালিয়ে যান")
        putBoth("Back", "পেছনে")
        putBoth("← Back", "← পেছনে")
        putBoth("Next", "পরবর্তী")
        putBoth("Done", "সম্পন্ন")
        putBoth("Skip", "এড়িয়ে যান")
        putBoth("Reset", "রিসেট")
        putBoth("Clear", "মুছুন")
        putBoth("Search", "খুঁজুন")
        putBoth("Filter", "ফিল্টার")
        putBoth("All", "সব")
        putBoth("ALL", "সব")
        putBoth("All Wallets", "সব ওয়ালেট")
        putBoth("All Categories", "সব ক্যাটাগরি")
        putBoth("All Types", "সব ধরন")
        putBoth("Active", "চলমান")
        putBoth("ACTIVE", "চলমান")
        putBoth("Settled", "পরিশোধিত")
        putBoth("SETTLED", "পরিশোধিত")
        putBoth("✓ Settled", "✓ পরিশোধিত")
        putBoth("PARTIALLY_PAID", "আংশিক পরিশোধিত")
        putBoth("Partially Paid", "আংশিক পরিশোধিত")
        putBoth("Overdue", "মেয়াদোত্তীর্ণ")
        putBoth("OVERDUE", "মেয়াদোত্তীর্ণ")
        putBoth("Archive", "আর্কাইভ")
        putBoth("Archived", "আর্কাইভ করা")
        putBoth("Unarchive", "আনআর্কাইভ")
        putBoth("Show Archived", "আর্কাইভ দেখুন")
        putBoth("Hide Archived", "আর্কাইভ লুকান")
        putBoth("Share", "শেয়ার")
        putBoth("Export", "এক্সপোর্ট")
        putBoth("Import", "ইমপোর্ট")
        putBoth("Restore", "রিস্টোর")
        putBoth("Copy", "কপি")
        putBoth("Paste", "পেস্ট")
        putBoth("Paste SMS", "এসএমএস পেস্ট করুন")
        putBoth("Auto-Fill", "অটো-ফিল")
        putBoth("Attach", "সংযুক্ত করুন")
        putBoth("Change", "পরিবর্তন")
        putBoth("Optional", "ঐচ্ছিক")
        putBoth("Required", "আবশ্যক")
        putBoth("Yes", "হ্যাঁ")
        putBoth("No", "না")
        putBoth("OK", "ঠিক আছে")
        putBoth("Got it", "বুঝেছি")
        putBoth("Unlock", "আনলক করুন")
        putBoth("Verify", "যাচাই করুন")
        putBoth("Activate", "চালু করুন")
        putBoth("Redeem", "রিডিম করুন")
        putBoth("Redeem Code", "কোড রিডিম করুন")
        putBoth("Upgrade", "আপগ্রেড")
        putBoth("Upgrade Now", "এখনই আপগ্রেড করুন")

        // Settings Screen
        putBoth("Language", "ভাষা")
        putBoth("App Language", "অ্যাপের ভাষা")
        putBoth("Select Language", "ভাষা নির্বাচন করুন")
        putBoth("English", "English")
        putBoth("Bangla", "বাংলা")
        putBoth("বাংলা", "বাংলা")
        putBoth("Your Name", "আপনার নাম")
        putBoth("Pocket User", "পকেট ব্যবহারকারী")
        putBoth("Edit Profile Name", "প্রোফাইল নাম পরিবর্তন")
        putBoth("App developed by Abdullahil Hadi", "অ্যাপ ডেভেলপার: আব্দুল্লাহিল হাদী")
        putBoth("👤 App developed by Abdullahil Hadi", "👤 অ্যাপ ডেভেলপার: আব্দুল্লাহিল হাদী")
        putBoth("App Developer", "অ্যাপ ডেভেলপার")
        putBoth("Abdullahil Hadi", "আব্দুল্লাহিল হাদী")
        putBoth("Phone", "ফোন")
        putBoth("WhatsApp", "হোয়াটসঅ্যাপ")
        putBoth("Email", "ইমেইল")
        putBoth("Facebook", "ফেসবুক")
        putBoth("Theme Mode", "থিম মোড")
        putBoth("Dark", "ডার্ক")
        putBoth("Light", "লাইট")
        putBoth("🌙 Dark Carbon", "🌙 ডার্ক কার্বন")
        putBoth("☀ Light Mode", "☀ লাইট মোড")
        putBoth("System", "সিস্টেম")
        putBoth("Accent Color", "অ্যাকসেন্ট রঙ")
        putBoth("Orange", "কমলা")
        putBoth("Teal", "টিল")
        putBoth("Blue", "নীল")
        putBoth("Violet", "বেগুনি")
        putBoth("Rose", "গোলাপী")
        putBoth("Custom Color", "কাস্টম রঙ")
        putBoth("Custom Accent Color", "কাস্টম অ্যাকসেন্ট রঙ")
        putBoth("Appearance & Wallpaper", "অ্যাপিয়ারেন্স ও ওয়ালপেপার")
        putBoth("Background Wallpaper", "ব্যাকগ্রাউন্ড ওয়ালপেপার")
        putBoth("Upload Wallpaper", "ওয়ালপেপার আপলোড করুন")
        putBoth("Change Wallpaper", "ওয়ালপেপার পরিবর্তন করুন")
        putBoth("Reset Wallpaper", "ওয়ালপেপার রিসেট করুন")
        putBoth("🖼 Custom Wallpaper", "🖼 কাস্টম ওয়ালপেপার")
        putBoth("Wallpaper Opacity", "ওয়ালপেপার অপাসিটি")
        putBoth("Background Weave / Wallpaper Opacity", "ব্যাকগ্রাউন্ড ও ওয়ালপেপার অপাসিটি")
        putBoth("Default Wallet for New Entries", "নতুন লেনদেনের ডিফল্ট ওয়ালেট")
        putBoth("Default Wallet", "ডিফল্ট ওয়ালেট")
        putBoth("Negative-balance warnings", "নেগেটিভ ব্যালেন্স সতর্কবার্তা")
        putBoth("Local App Lock (PIN)", "অ্যাপ লক (পিন)")
        putBoth("4-Digit App Lock PIN (Optional)", "৪-ডিজিট অ্যাপ লক পিন (ঐচ্ছিক)")
        putBoth("Leave empty to disable PIN lock", "পিন লক বন্ধ রাখতে খালি রাখুন")
        putBoth("Biometric Fingerprint / Face Unlock", "বায়োমেট্রিক ফিঙ্গারপ্রিন্ট / ফেস আনলক")
        putBoth("Notification Menu", "নোটিফিকেশন মেনু")
        putBoth("Loan Deadline Notification", "ঋণের শেষ তারিখের নোটিফিকেশন")
        putBoth("Daily Log Entry Reminder", "প্রতিদিনের হিসাব লেখার রিমাইন্ডার")
        putBoth("Daily Reminder Time", "রিমাইন্ডারের সময়")
        putBoth("Reminder Time", "রিমাইন্ডারের সময়")
        putBoth("Financial Modules & Tools", "আর্থিক মডিউল ও টুলস")
        putBoth("🔁 Recurring Bills & Subscriptions", "🔁 নিয়মিত বিল ও সাবস্ক্রিপশন")
        putBoth("🏷 Categories & Monthly Budgets", "🏷 ক্যাটাগরি ও মাসিক বাজেট")
        putBoth("⚖ Reconcile Wallet Balance", "⚖ ওয়ালেট ব্যালেন্স সমন্বয়")
        putBoth("💾 JSON Backup & Restore", "💾 জেসন ব্যাকআপ ও রিস্টোর")
        putBoth("Budget & Security", "বাজেট ও নিরাপত্তা")
        putBoth("Master Monthly Spending Budget (৳)", "মাসিক মোট খরচের বাজেট (৳)")
        putBoth("Master Monthly Budget", "মাসিক মূল বাজেট")
        putBoth("0 (Disabled)", "০ (বন্ধ)")
        putBoth("Reset All Numerical Data to 0", "সব সংখ্যার হিসাব ০-তে রিসেট করুন")
        putBoth("Reset All Numerical Data", "সব সংখ্যার হিসাব রিসেট করুন")
        putBoth("Wipe All Local Data", "সব লোকাল ডেটা মুছে ফেলুন")
        putBoth("Danger Zone", "সতর্কতা জোন")
        putBoth("Data & Backup", "ডেটা ও ব্যাকআপ")

        // Subscription & Premium
        putBoth("Free Tier Plan", "ফ্রি প্ল্যান")
        putBoth("Lifetime Premium", "লাইফটাইম প্রিমিয়াম")
        putBoth("3 Days Premium", "৩ দিনের প্রিমিয়াম")
        putBoth("Pocket Premium", "পকেট প্রিমিয়াম")
        putBoth("Upgrade to Pocket Premium", "পকেট প্রিমিয়ামে আপগ্রেড করুন")
        putBoth("All features unlocked", "সব ফিচার আনলক করা আছে")
        putBoth("All Premium features unlocked", "সব প্রিমিয়াম ফিচার আনলক করা আছে")
        putBoth("Enter Activation Code", "অ্যাক্টিভেশন কোড দিন")
        putBoth("Activation Code", "অ্যাক্টিভেশন কোড")
        putBoth("Have an activation code?", "আপনার কি অ্যাক্টিভেশন কোড আছে?")
        putBoth("Buy Activation Code", "অ্যাক্টিভেশন কোড কিনুন")
        putBoth("Contact on WhatsApp to Buy", "কিনতে হোয়াটসঅ্যাপে যোগাযোগ করুন")
        putBoth("🛡️ Admin: Manage Activation Codes", "🛡️ অ্যাডমিন: অ্যাক্টিভেশন কোড ম্যানেজার")
        putBoth("Admin: Manage Activation Codes", "অ্যাডমিন: অ্যাক্টিভেশন কোড ম্যানেজার")

        // Cloud Sync & Auth
        putBoth("Google Account Cloud Sync", "গুগল ক্লাউড সিঙ্ক")
        putBoth("☁ Google Account Cloud Sync", "☁ গুগল ক্লাউড সিঙ্ক")
        putBoth("Sign in with Google", "গুগল দিয়ে সাইন ইন করুন")
        putBoth("Sign Out", "সাইন আউট")
        putBoth("Sync Now", "এখনই সিঙ্ক করুন")
        putBoth("Syncing...", "সিঙ্ক হচ্ছে...")
        putBoth("Delete Account", "অ্যাকাউন্ট মুছুন")
        putBoth("Permanently remove cloud account", "স্থায়ীভাবে ক্লাউড অ্যাকাউন্ট মুছুন")
        putBoth("Currently using Pocket offline. Sign in with Google to switch to your cloud backup.", "বর্তমানে অফলাইনে পকেট ব্যবহার করছেন। ক্লাউড ব্যাকআপ ব্যবহার করতে গুগল দিয়ে সাইন ইন করুন।")
        putBoth("Offline mode (not signed in)", "অফলাইন মোড (সাইন ইন করা নেই)")
        putBoth("Sign Out of Google Account?", "গুগল অ্যাকাউন্ট থেকে সাইন আউট করবেন?")
        putBoth("Delete Account Permanently?", "অ্যাকাউন্ট স্থায়ীভাবে মুছে ফেলবেন?")
        putBoth("Delete Everything", "সবকিছু মুছে ফেলুন")
        putBoth("⚠ Local Data Will Be Gone Forever", "⚠ লোকাল ডেটা চিরতরে মুছে যাবে")
        putBoth("Local Data Will Be Gone Forever", "লোকাল ডেটা চিরতরে মুছে যাবে")
        putBoth("🗑 Wipe Local Data & Continue", "🗑 লোকাল ডেটা মুছে চালিয়ে যান")
        putBoth("Wipe Local Data & Continue", "লোকাল ডেটা মুছে চালিয়ে যান")
        putBoth("☁ Keep & Backup Local Data to Google", "☁ লোকাল ডেটা রেখে গুগলে ব্যাকআপ করুন")
        putBoth("Keep & Backup Local Data to Google", "লোকাল ডেটা রেখে গুগলে ব্যাকআপ করুন")
        putBoth("Cancel (Keep Local Data)", "বাতিল (লোকাল ডেটা রাখুন)")

        // Onboarding & Startup
        putBoth("Welcome to Pocket", "পকেটে স্বাগতম")
        putBoth("Personal Finance, Wallets & Loan Khata", "ব্যক্তিগত হিসাব, ওয়ালেট ও লোন খাতা")
        putBoth("Continue Offline", "অফলাইনে চালিয়ে যান")
        putBoth("Continue without logging in", "লগইন ছাড়াই চালিয়ে যান")
        putBoth("Switch account", "অ্যাকাউন্ট পরিবর্তন করুন")
        putBoth("Enter your name", "আপনার নাম লিখুন")
        putBoth("Start Using Pocket", "পকেট ব্যবহার শুরু করুন")
        putBoth("Complete Setup", "সেটআপ সম্পন্ন করুন")
        putBoth("Select Your Wallets", "আপনার ওয়ালেটগুলো নির্বাচন করুন")
        putBoth("Choose Wallets & Current Balances", "ওয়ালেট ও বর্তমান ব্যালেন্স নির্বাচন করুন")
        putBoth("Show More Wallets", "আরও ওয়ালেট দেখুন")
        putBoth("Show Fewer Wallets", "কম ওয়ালেট দেখুন")
        putBoth("Add Custom Wallet", "কাস্টম ওয়ালেট যোগ করুন")
        putBoth("Existing Loans (Optional)", "পূর্বের ঋণ ও পাওনা (ঐচ্ছিক)")
        putBoth("Tap to add existing loans (Lent / Borrowed)", "পূর্বের ঋণ বা পাওনা যোগ করতে ট্যাপ করুন")
        putBoth("Current Balance (Optional)", "বর্তমান ব্যালেন্স (ঐচ্ছিক)")
        putBoth("Load sample transactions to explore", "দেখার জন্য নমুনা লেনদেন যুক্ত করুন")

        // Wallets & Balances Screen
        putBoth("Total Balance", "মোট ব্যালেন্স")
        putBoth("Total Wallet Balance", "মোট ওয়ালেট ব্যালেন্স")
        putBoth("Included Wallets", "অন্তর্ভুক্ত ওয়ালেট")
        putBoth("Excluded from Total", "মোট হিসাবের বাইরে")
        putBoth("Include in Total Wallet Balance", "মোট ওয়ালেট ব্যালেন্সে অন্তর্ভুক্ত করুন")
        putBoth("Include in Total Balance", "মোট ব্যালেন্সে অন্তর্ভুক্ত করুন")
        putBoth("New Wallet", "নতুন ওয়ালেট")
        putBoth("Edit Wallet", "ওয়ালেট সম্পাদনা")
        putBoth("Save Wallet", "ওয়ালেট সংরক্ষণ করুন")
        putBoth("Wallet Name", "ওয়ালেটের নাম")
        putBoth("Wallet Icon", "ওয়ালেটের আইকন")
        putBoth("Opening Balance", "প্রারম্ভিক ব্যালেন্স")
        putBoth("Current Balance", "বর্তমান ব্যালেন্স")
        putBoth("Calculated App Balance", "অ্যাপের হিসাবকৃত ব্যালেন্স")
        putBoth("Actual Real-World Balance Today", "আজকের প্রকৃত ব্যালেন্স")
        putBoth("Actual Balance", "প্রকৃত ব্যালেন্স")
        putBoth("Save Reconciliation", "সমন্বয় সংরক্ষণ করুন")
        putBoth("Savings Goals", "সেভিংস লক্ষ্য")
        putBoth("New Savings Goal", "নতুন সেভিংস লক্ষ্য")
        putBoth("Edit Savings Goal", "সেভিংস লক্ষ্য সম্পাদনা")
        putBoth("Goal Title", "লক্ষ্যের নাম")
        putBoth("Target Amount (৳)", "লক্ষ্যমাত্রা (৳)")
        putBoth("Target Amount", "লক্ষ্যমাত্রা")
        putBoth("Saved So Far (৳)", "এ পর্যন্ত জমা (৳)")
        putBoth("Saved So Far", "এ পর্যন্ত জমা")
        putBoth("Save Goal", "লক্ষ্য সংরক্ষণ করুন")
        putBoth("Add Savings", "জমা যোগ করুন")
        putBoth("Credit Limit", "ক্রেডিট লিমিট")
        putBoth("Available Credit", "ব্যবহারযোগ্য ক্রেডিট")
        putBoth("Used Credit", "ব্যবহৃত ক্রেডিট")
        putBoth("Statement Day", "স্টেটমেন্টের দিন")
        putBoth("Statement Day (1-31)", "স্টেটমেন্টের দিন (১-৩১)")
        putBoth("Payment Due Day", "পরিশোধের শেষ দিন")
        putBoth("Payment Due Day (1-31)", "পরিশোধের শেষ দিন (১-৩১)")
        putBoth("No wallet found", "কোনো ওয়ালেট পাওয়া যায়নি")
        putBoth("No wallet", "কোনো ওয়ালেট নেই")
        putBoth("Tap to open", "খুলতে ট্যাপ করুন")

        // Transactions & Transfers
        putBoth("Income", "আয়")
        putBoth("INCOME", "আয়")
        putBoth("+ Income", "+ আয়")
        putBoth("Expense", "ব্যয়")
        putBoth("EXPENSE", "ব্যয়")
        putBoth("− Expense", "− ব্যয়")
        putBoth("Expense Reversal", "ব্যয় রিফান্ড")
        putBoth("EXPENSE_REVERSAL", "ব্যয় রিফান্ড")
        putBoth("Refund / Expense Reversal", "রিফান্ড / ব্যয় সমন্বয়")
        putBoth("Transfer", "ট্রান্সফার")
        putBoth("TRANSFER", "ট্রান্সফার")
        putBoth("Wallet Transfer", "ওয়ালেট ট্রান্সফার")
        putBoth("Complete Transfer", "ট্রান্সফার সম্পন্ন করুন")
        putBoth("From Wallet", "যে ওয়ালেট থেকে")
        putBoth("To Wallet", "যে ওয়ালেটে যাবে")
        putBoth("Source Wallet", "উৎস ওয়ালেট")
        putBoth("Destination Wallet", "গন্তব্য ওয়ালেট")
        putBoth("Transfer Amount", "ট্রান্সফারের পরিমাণ")
        putBoth("Transfer Fee (Optional)", "ট্রান্সফার ফি (ঐচ্ছিক)")
        putBoth("Transfer Fee", "ট্রান্সফার ফি")
        putBoth("Service Fee / Charge", "সার্ভিস ফি / চার্জ")
        putBoth("Fee / Charge (Optional)", "ফি / চার্জ (ঐচ্ছিক)")
        putBoth("New Record", "নতুন হিসাব")
        putBoth("Edit Record", "হিসাব সম্পাদনা")
        putBoth("New Transaction", "নতুন লেনদেন")
        putBoth("Edit Transaction", "লেনদেন সম্পাদনা")
        putBoth("Amount", "পরিমাণ")
        putBoth("Amount (৳)", "পরিমাণ (৳)")
        putBoth("Amount (Supports inline math: e.g. 250+80*2)", "পরিমাণ (হিসাব করা যাবে: যেমন ২৫০+৮০*২)")
        putBoth("Date", "তারিখ")
        putBoth("Time", "সময়")
        putBoth("Note", "নোট")
        putBoth("Reason / Note", "কারণ / নোট")
        putBoth("What was this for?", "এটি কিসের জন্য ছিল?")
        putBoth("Reference / TrxID", "রেফারেন্স / TrxID")
        putBoth("Reference", "রেফারেন্স")
        putBoth("Tags", "ট্যাগ")
        putBoth("Split Transaction", "বিভক্ত লেনদেন (Split)")
        putBoth("Split by Category", "ক্যাটাগরি অনুযায়ী ভাগ করুন")
        putBoth("Add Split Row", "আরেকটি ভাগ যোগ করুন")
        putBoth("Receipt / Bill Photo (Optional)", "রশিদ / বিলের ছবি (ঐচ্ছিক)")
        putBoth("Warranty slip, bill, or MFS screenshot", "ওয়ারেন্টি স্লিপ, বিল বা এমএফএস স্ক্রিনশট")
        putBoth("Photo Attached", "ছবি সংযুক্ত করা হয়েছে")
        putBoth("Attached Photo", "সংযুক্ত ছবি")
        putBoth("Tap thumbnail to view full size", "পুরো ছবি দেখতে থাম্বনেইলে ট্যাপ করুন")
        putBoth("Paste MFS / Bank SMS", "এমএফএস / ব্যাংক এসএমএস পেস্ট করুন")
        putBoth("SMS Text", "এসএমএস টেক্সট")
        putBoth("Could not detect a valid amount in this SMS.", "এই এসএমএস-এ কোনো সঠিক টাকার পরিমাণ পাওয়া যায়নি।")
        putBoth("No transactions yet", "এখনো কোনো লেনদেন নেই")
        putBoth("No matching transactions", "কোনো লেনদেন পাওয়া যায়নি")
        putBoth("Recent Transactions", "সাম্প্রতিক লেনদেন")
        putBoth("View All", "সব দেখুন")
        putBoth("See All", "সব দেখুন")

        // Loans & Khata
        putBoth("You Will Get (Lent)", "আপনি পাবেন (পাওনা)")
        putBoth("You Will Get", "আপনি পাবেন")
        putBoth("You Owe (Borrowed)", "আপনি দেবেন (দেনা)")
        putBoth("You Owe", "আপনি দেবেন")
        putBoth("I Lent", "আমি ধার দিয়েছি")
        putBoth("I Borrowed", "আমি ধার নিয়েছি")
        putBoth("I Lent Money (Will Receive Back)", "আমি ধার দিয়েছি (ফেরত পাব)")
        putBoth("I Borrowed Money (Need to Pay)", "আমি ধার নিয়েছি (পরিশোধ করতে হবে)")
        putBoth("Lent", "পাওনা (প্রদত্ত)")
        putBoth("LENT", "পাওনা")
        putBoth("Borrowed", "দেনা (গৃহীত)")
        putBoth("BORROWED", "দেনা")
        putBoth("Active Loans", "চলমান ঋণ")
        putBoth("All Loans", "সব ঋণ")
        putBoth("Person View", "ব্যক্তিভিত্তিক খাতা")
        putBoth("Person Khata", "ব্যক্তিভিত্তিক খাতা")
        putBoth("New Loan Record", "নতুন ঋণের হিসাব")
        putBoth("New Loan", "নতুন ঋণ")
        putBoth("Save Loan", "ঋণ সংরক্ষণ করুন")
        putBoth("Person Name", "ব্যক্তির নাম")
        putBoth("Principal Amount (৳)", "আসল পরিমাণ (৳)")
        putBoth("Principal Amount", "আসল পরিমাণ")
        putBoth("Deadline Date (Optional)", "পরিশোধের শেষ তারিখ (ঐচ্ছিক)")
        putBoth("Due Date", "শেষ তারিখ")
        putBoth("Receive Payment", "টাকা গ্রহণ করুন")
        putBoth("Pay Installment", "কিস্তি পরিশোধ করুন")
        putBoth("Confirm Settlement", "পরিশোধ নিশ্চিত করুন")
        putBoth("Record Repayment", "পরিশোধ রেকর্ড করুন")
        putBoth("Add More Loan", "আরও ঋণ যোগ করুন")
        putBoth("No loans in this view.", "এই তালিকায় কোনো ঋণ নেই।")
        putBoth("Update selected wallet balance today (uncheck if this is an older existing loan)", "আজই নির্বাচিত ওয়ালেট ব্যালেন্স আপডেট করুন (পুরোনো ঋণ হলে টিক তুলে দিন)")

        // Reports & Statements
        putBoth("Total Income", "মোট আয়")
        putBoth("Total Expense", "মোট ব্যয়")
        putBoth("Total Expenses", "মোট ব্যয়")
        putBoth("Net Savings", "নিট সঞ্চয়")
        putBoth("Net Cashflow", "নিট ক্যাশফ্লো")
        putBoth("Net Change", "নিট পরিবর্তন")
        putBoth("Closing Balance", "সমাপনী ব্যালেন্স")
        putBoth("Expense Breakdown", "ব্যয়ের খাতওয়ারী হিসাব")
        putBoth("Expense Breakdown by Category", "ক্যাটাগরি অনুযায়ী ব্যয়ের হিসাব")
        putBoth("Income Breakdown", "আয়ের খাতওয়ারী হিসাব")
        putBoth("Export PDF", "পিডিএফ (PDF) এক্সপোর্ট")
        putBoth("Export CSV", "সিএসভি (CSV) এক্সপোর্ট")
        putBoth("Download PDF Statement", "পিডিএফ স্টেটমেন্ট ডাউনলোড করুন")
        putBoth("Share PDF Statement", "পিডিএফ স্টেটমেন্ট শেয়ার করুন")
        putBoth("Monthly Budget", "মাসিক বাজেট")
        putBoth("Monthly Budget (Optional)", "মাসিক বাজেট (ঐচ্ছিক)")
        putBoth("Set Budget", "বাজেট নির্ধারণ করুন")

        // Categories & Recurring
        putBoth("New Category", "নতুন ক্যাটাগরি")
        putBoth("Category", "ক্যাটাগরি")
        putBoth("+ Category", "+ ক্যাটাগরি")
        putBoth("New Expense Category", "নতুন ব্যয়ের ক্যাটাগরি")
        putBoth("New Income Category", "নতুন আয়ের ক্যাটাগরি")
        putBoth("Edit Category", "ক্যাটাগরি সম্পাদনা")
        putBoth("Edit Expense Category", "ব্যয়ের ক্যাটাগরি সম্পাদনা")
        putBoth("Edit Income Category", "আয়ের ক্যাটাগরি সম্পাদনা")
        putBoth("Category Name", "ক্যাটাগরির নাম")
        putBoth("Save Category", "ক্যাটাগরি সংরক্ষণ করুন")
        putBoth("Drag here to Delete Category", "ক্যাটাগরি মুছতে এখানে টেনে আনুন")
        putBoth("Release to Delete Category", "ক্যাটাগরি মুছতে ছেড়ে দিন")
        putBoth("Date of Giving Loan", "ধার দেওয়ার তারিখ")
        putBoth("Date of Taking Loan", "ধার নেওয়ার তারিখ")
        putBoth("Income Categories", "আয়ের ক্যাটাগরি")
        putBoth("Expense Categories", "ব্যয়ের ক্যাটাগরি")
        putBoth("Financial Modules", "আর্থিক মডিউল")
        putBoth("Enable Fund Feature", "ফান্ড ফিচার চালু করুন")
        putBoth("Add Tags (Optional)", "ট্যাগ যোগ করুন (ঐচ্ছিক)")
        putBoth("Fund feature enabled.", "ফান্ড ফিচার চালু করা হয়েছে।")
        putBoth("Fund feature disabled.", "ফান্ড ফিচার বন্ধ করা হয়েছে।")
        putBoth("Pull down to sync", "সিঙ্ক করতে নিচে টানুন")
        putBoth("Release to sync with Cloud", "ক্লাউড সিঙ্ক করতে ছেড়ে দিন")
        putBoth("Synced latest changes from Google Cloud.", "গুগল ক্লাউড থেকে সর্বশেষ তথ্য সিঙ্ক করা হয়েছে।")
        putBoth("Wallet Balance Reconciliation", "ওয়ালেট ব্যালেন্স সমন্বয়")
        putBoth("Widgets", "উইজেট")
        putBoth("Monthly Statement", "মাসিক স্টেটমেন্ট")
        putBoth("Backup", "ব্যাকআপ")
        putBoth("Preferences", "পছন্দসমূহ")
        putBoth("App theme", "অ্যাপ থিম")
        putBoth("Accent color", "অ্যাকসেন্ট রঙ")
        putBoth("Background wallpaper & Opacity", "ব্যাকগ্রাউন্ড ওয়ালপেপার ও অপাসিটি")
        putBoth("Box Color", "বক্সের রঙ")
        putBoth("Box Opacity", "বক্সের অপাসিটি")
        putBoth("Carbon", "কার্বন")
        putBoth("Slate", "স্লেট")
        putBoth("Midnight", "মিডনাইট")
        putBoth("Emerald", "এমেরাল্ড")
        putBoth("Burgundy", "বারগান্ডি")
        putBoth("Reset", "রিসেট")
        putBoth("Default wallet for new Entries", "নতুন লেনদেনের ডিফল্ট ওয়ালেট")
        putBoth("Security", "নিরাপত্তা")
        putBoth("App Lock (PIN)", "অ্যাপ লক (পিন)")
        putBoth("Biometric unlock", "বায়োমেট্রিক আনলক")
        putBoth("Reset all Data to zero", "সব ডেটা শূন্য (০) করুন")
        putBoth("Reset All Data to Zero", "সব ডেটা শূন্য (০) করুন")
        putBoth("Feedback & Bugs Report", "মতামত ও বাগ রিপোর্ট")
        putBoth("Subject", "বিষয়")
        putBoth("Details", "বিস্তারিত")
        putBoth("Submit", "জমা দিন")
        putBoth("Please enter a subject or details.", "অনুগ্রহ করে বিষয় অথবা বিস্তারিত লিখুন।")
        putBoth("Show less wallets", "কম ওয়ালেট দেখুন")
        putBoth("Existing loan", "পূর্বের ঋণ ও পাওনা")
        putBoth("💖 Acknowledgement: Special Thanks to my beloved wife Mahadia Afruj for inspiring core architecture and endless support.", "💖 কৃতজ্ঞতা স্বীকার: মূল আর্কিটেকচারের অনুপ্রেরণা এবং অফুরন্ত সমর্থনের জন্য আমার প্রিয়তমা স্ত্রী মাহাদিয়া আফরুজকে বিশেষ ধন্যবাদ।")
        putBoth("💖 Acknowledgement 💖", "💖 কৃতজ্ঞতা স্বীকার 💖")
        putBoth("Acknowledgement", "কৃতজ্ঞতা স্বীকার")
        putBoth("Special Thanks to my beloved wife", "বিশেষ ধন্যবাদ আমার প্রিয়তমা স্ত্রী")
        putBoth("Mahadia Afruj", "মাহাদিয়া আফরুজ")
        putBoth("💖 Mahadia Afruj 💖", "💖 মাহাদিয়া আফরুজ 💖")
        putBoth("for inspiring core architecture, her love and endless support.", "মূল আর্কিটেকচারের অনুপ্রেরণা, তাঁর ভালোবাসা এবং অফুরন্ত সমর্থনের জন্য।")
        putBoth("Deadline Date (Optional - Tap to pick)", "পরিশোধের শেষ তারিখ (ঐচ্ছিক - নির্বাচন করতে ট্যাপ করুন)")
        putBoth("Choose Custom Accent Color", "কাস্টম অ্যাকসেন্ট রঙ নির্বাচন করুন")
        putBoth("Choose Custom Box Color", "কাস্টম বক্স রঙ নির্বাচন করুন")
        putBoth("Selected Accent", "নির্বাচিত অ্যাকসেন্ট")
        putBoth("Selected Box Color", "নির্বাচিত বক্স রঙ")
        putBoth("HEX Code", "হেক্স কোড (HEX)")
        putBoth("Thank you! Your feedback has been sent.", "ধন্যবাদ! আপনার মতামত পাঠানো হয়েছে।")
        putBoth("Send Report", "রিপোর্ট পাঠান")
        putBoth("Admin Inbox", "অ্যাডমিন ইনবক্স")
        putBoth("Sending From", "প্রেরকের অ্যাকাউন্ট")
        putBoth("Adjust Current Balance", "বর্তমান ব্যালেন্স সমন্বয়")
        putBoth("Reconciliation", "সমন্বয়ের কারণ")
        putBoth("Missing", "হিসাব বাদ পড়েছে")
        putBoth("No source", "উৎস অজানা")
        putBoth("Other", "অন্যান্য")
        putBoth("Reconciliation Note", "সমন্বয়ের নোট")
        putBoth("Reason for balance adjustment", "ব্যালেন্স সমন্বয়ের কারণ")
        putBoth("Person Name", "ব্যক্তির নাম")
        putBoth("Person or Institution Name", "ব্যক্তি বা প্রতিষ্ঠানের নাম")
        putBoth("Attach Loan Proof (Optional)", "ঋণের প্রমাণ সংযুক্ত করুন (ঐচ্ছিক)")
        putBoth("Tap to pick calendar date", "ক্যালেন্ডার থেকে তারিখ নির্বাচন করুন")
        putBoth("System", "সিস্টেম")
        putBoth("New Recurring Rule", "নতুন নিয়মিত বিল")
        putBoth("Save Recurring Rule", "নিয়মিত বিল সংরক্ষণ করুন")
        putBoth("Next Due Date", "পরবর্তী পরিশোধের তারিখ")
        putBoth("Log & Advance Next Due Date", "হিসাব যুক্ত করুন ও পরবর্তী তারিখ এগিয়ে দিন")
        putBoth("Billing Day of Month (1–31)", "মাসের বিল পরিশোধের দিন (১–৩১)")
        putBoth("Repeat Every X Days (e.g., 14, 45, 90)", "কত দিন পরপর পুনরাবৃত্তি হবে (যেমন: ১৪, ৪৫, ৯০)")
        putBoth("Pay", "পরিশোধ")
        putBoth("Receive", "গ্রহণ")
        putBoth("Receive / Log", "গ্রহণ / লগ করুন")
        putBoth("Remind Tomorrow", "আগামীকাল মনে করিয়ে দিন")
        putBoth("Manual", "ম্যানুয়াল")
        putBoth("Manual amount input", "ম্যানুয়াল টাকার পরিমাণ")
        putBoth("Fixed", "নির্ধারিত")
        putBoth("DAILY", "প্রতিদিন")
        putBoth("Daily", "প্রতিদিন")
        putBoth("WEEKLY", "সাপ্তাহিক")
        putBoth("Weekly", "সাপ্তাহিক")
        putBoth("MONTHLY", "মাসিক")
        putBoth("Monthly", "মাসিক")
        putBoth("QUARTERLY", "ত্রৈমাসিক")
        putBoth("Quarterly", "ত্রৈমাসিক")
        putBoth("YEARLY", "বার্ষিক")
        putBoth("Yearly", "বার্ষিক")
        putBoth("CUSTOM", "কাস্টম")
        putBoth("Custom", "কাস্টম")
        putBoth("CUSTOM_DAYS", "কাস্টম দিন")
        putBoth("Custom Days", "কাস্টম দিন")

        // Notifications & Status Messages
        putBoth("Daily Check-In: Log Today's Entries", "দৈনিক রিমাইন্ডার: আজকের হিসাব লিখুন")
        putBoth("Take a moment to record today's income and expenses so your Pocket wallet balances stay accurate.", "আপনার ওয়ালেটের ব্যালেন্স সঠিক রাখতে আজকের আয় ও ব্যয়ের হিসাব লিখে রাখুন।")
        putBoth("Master monthly spending budget cleared.", "মাসিক মূল খরচের বাজেট মুছে ফেলা হয়েছে।")
        putBoth("Loan, card & recurring bill reminders synced.", "ঋণ, কার্ড ও নিয়মিত বিলের রিমাইন্ডার সিঙ্ক করা হয়েছে।")
        putBoth("All income, expenses, loans, transfers, and wallet balances have been reset to 0.", "সব আয়, ব্যয়, ঋণ, ট্রান্সফার এবং ওয়ালেট ব্যালেন্স ০-তে রিসেট করা হয়েছে।")
        putBoth("Sign-in cancelled. Staying offline with current local data.", "সাইন-ইন বাতিল করা হয়েছে। বর্তমান লোকাল ডেটা নিয়ে অফলাইনেই থাকা হচ্ছে।")
        putBoth("Local data deleted. Set up wallets for your Google Account.", "লোকাল ডেটা মুছে ফেলা হয়েছে। আপনার গুগল অ্যাকাউন্টের জন্য ওয়ালেট সেটআপ করুন।")
        putBoth("Synced from another device", "অন্য ডিভাইস থেকে সিঙ্ক হয়েছে")
        putBoth("Syncing with Google Cloud...", "গুগল ক্লাউডের সাথে সিঙ্ক হচ্ছে...")
        putBoth("Backed up to Google Cloud", "গুগল ক্লাউডে ব্যাকআপ হয়েছে")

        // Period Labels
        putBoth("This Month", "এই মাস")
        putBoth("Last Month", "গত মাস")

        // Fund Wallets & Personal Borrow
        putBoth("Fund", "ফান্ড")
        putBoth("Combined Fund", "সম্মিলিত ফান্ড")
        putBoth("Combined Fund Details", "সম্মিলিত ফান্ডের বিস্তারিত")
        putBoth("Auto-Summed", "স্বয়ংক্রিয় যোগফল")
        putBoth("Auto-Summed Virtual Fund Across All Wallets", "সব ওয়ালেটের সম্মিলিত ভার্চুয়াল ফান্ড")
        putBoth("Total Fund Balance", "মোট ফান্ড ব্যালেন্স")
        putBoth("Virtual Fund Option", "ভার্চুয়াল ফান্ড অপশন")
        putBoth("Track official/investment fund & personal borrow inside this wallet", "এই ওয়ালেটের ভেতরে অফিসিয়াল/ইনভেস্টমেন্ট ফান্ড ও ব্যক্তিগত ধার হিসাব করুন")
        putBoth("Initial / Opening Fund in this Wallet (৳)", "এই ওয়ালেটে প্রারম্ভিক ফান্ডের পরিমাণ (৳)")
        putBoth("Virtual Fund (Inside this Wallet)", "ভার্চুয়াল ফান্ড (এই ওয়ালেটের ভেতরে)")
        putBoth("Fund Balance", "ফান্ড ব্যালেন্স")
        putBoth("Personal Balance", "ব্যক্তিগত ব্যালেন্স")
        putBoth("Personal", "ব্যক্তিগত")
        putBoth("Personal Income", "ব্যক্তিগত আয়")
        putBoth("Add to Fund", "ফান্ডে যোগ")
        putBoth("Official Spent", "অফিসিয়াল খরচ")
        putBoth("Fund Breakdown by Wallet", "ওয়ালেট অনুযায়ী ফান্ডের হিসাব")
        putBoth("Fund Activity History", "ফান্ডের লেনদেনের ইতিহাস")
        putBoth("No fund transactions recorded yet.", "এখনো কোনো ফান্ড লেনদেন রেকর্ড করা হয়নি।")
        putBoth("Fund Transfer", "ফান্ড ট্রান্সফার")
        putBoth("Keep as Fund", "ফান্ড হিসেবে রাখুন")
        putBoth("Tracker", "ট্র্যাকার")
        putBoth("Pay From Wallet (Personal Balance)", "যে ওয়ালেট থেকে ফেরত দেবেন (ব্যক্তিগত ব্যালেন্স)")
        putBoth("Add Returned Money To Fund In", "যে ওয়ালেটের ফান্ডে জমা হবে")
        putBoth("Investment / project fund", "ইনভেস্টমেন্ট / প্রজেক্ট ফান্ড")
        putBoth("Regular (Fund Expense)", "নিয়মিত (ফান্ডের খরচ)")
        putBoth("Fund Expense", "ফান্ডের খরচ")
        putBoth("Borrow from fund", "ফান্ড থেকে ধার")
        putBoth("New category", "নতুন ক্যাটাগরি")
        putBoth("Save & Add another", "সেভ ও আরেকটি যোগ করুন")
        putBoth("Source", "উৎস")
        putBoth("Destination", "গন্তব্য")
        putBoth("Transfer Charge", "ট্রান্সফার চার্জ")
        putBoth("Edit Income", "আয় সম্পাদনা")
        putBoth("Edit Expense", "ব্যয় সম্পাদনা")
        putBoth("Edit Transfer", "ট্রান্সফার সম্পাদনা")
        putBoth("Personal Borrow", "ব্যক্তিগত ধার")
        putBoth("Personal Borrowed", "ব্যক্তিগত ধার")
        putBoth("Personal Borrowed Due", "ব্যক্তিগত ধার বকেয়া")
        putBoth("Borrowed Due", "ধার বকেয়া")
        putBoth("Return to Fund", "ফান্ডে ফেরত দিন")
        putBoth("Return Money", "টাকা ফেরত দিন")
        putBoth("Return Amount (৳)", "ফেরতের পরিমাণ (৳)")
        putBoth("Transfer From Wallet", "কোন ওয়ালেট থেকে ফেরত দেবেন")
        putBoth("Regular Transfer", "নিয়মিত ট্রান্সফার")
        putBoth("Return Borrowed", "ধার ফেরত")
        putBoth("Returned to Fund", "ফান্ডে ফেরত")
        putBoth("50% Return", "৫০% ফেরত")
        putBoth("100% Full Due", "১০০% সম্পূর্ণ বকেয়া")
        putBoth("Savings Goals", "সঞ্চয় লক্ষ্য")
        putBoth("Add Goal", "লক্ষ্য যোগ করুন")
        putBoth("New Savings Goal", "নতুন সঞ্চয় লক্ষ্য")
        putBoth("Edit Savings Goal", "সঞ্চয় লক্ষ্য সম্পাদনা")
    }

    private val monthReplacements = listOf(
        "January" to "জানুয়ারি",
        "February" to "ফেব্রুয়ারি",
        "March" to "মার্চ",
        "April" to "এপ্রিল",
        "May" to "মে",
        "June" to "জুন",
        "July" to "জুলাই",
        "August" to "আগস্ট",
        "September" to "সেপ্টেম্বর",
        "October" to "অক্টোবর",
        "November" to "নভেম্বর",
        "December" to "ডিসেম্বর",
        "Jan" to "জানু",
        "Feb" to "ফেব্রু",
        "Mar" to "মার্চ",
        "Apr" to "এপ্রিল",
        "Jun" to "জুন",
        "Jul" to "জুলাই",
        "Aug" to "আগস্ট",
        "Sep" to "সেপ্টে",
        "Oct" to "অক্টো",
        "Nov" to "নভে",
        "Dec" to "ডিসে"
    )

    private val weekdayReplacements = listOf(
        "Monday" to "সোমবার",
        "Tuesday" to "মঙ্গলবার",
        "Wednesday" to "বুধবার",
        "Thursday" to "বৃহস্পতিবার",
        "Friday" to "শুক্রবার",
        "Saturday" to "শনিবার",
        "Sunday" to "রবিবার",
        "Mon" to "সোম",
        "Tue" to "মঙ্গল",
        "Wed" to "বুধ",
        "Thu" to "বৃহঃ",
        "Fri" to "শুক্র",
        "Sat" to "শনি",
        "Sun" to "রবি"
    )

    private val prefixLabelReplacements = listOf(
        "Balance: " to "ব্যালেন্স: ",
        "Bal: " to "ব্যালেন্স: ",
        "Current Balance: " to "বর্তমান ব্যালেন্স: ",
        "Opening Balance: " to "প্রারম্ভিক ব্যালেন্স: ",
        "Closing Balance: " to "সমাপনী ব্যালেন্স: ",
        "Outstanding: " to "বকেয়া: ",
        "Remaining: " to "বাকি: ",
        "Repaid so far: " to "এ পর্যন্ত পরিশোধ: ",
        "Repaid: " to "পরিশোধিত: ",
        "Principal: " to "আসল: ",
        "Principal " to "আসল ",
        "Interest: " to "সুদ/চার্জ: ",
        "Fee: " to "ফি: ",
        "Service charge: " to "সার্ভিস চার্জ: ",
        "Budget: " to "বাজেট: ",
        "Spent: " to "খরচ: ",
        "Saved: " to "জমা: ",
        "Target: " to "লক্ষ্য: ",
        "Limit: " to "লিমিট: ",
        "Available: " to "ব্যবহারযোগ্য: ",
        "Difference: " to "পার্থক্য: ",
        "Lent to " to "ধার দেওয়া হয়েছে: ",
        "Borrowed from " to "ধার নেওয়া হয়েছে: ",
        "Received from " to "গ্রহণ করা হয়েছে: ",
        "Paid to " to "পরিশোধ করা হয়েছে: ",
        "Receive from " to "টাকা গ্রহণ: ",
        "Transfer: " to "ট্রান্সফার: ",
        "Next due " to "পরবর্তী তারিখ ",
        "Due: " to "শেষ তারিখ: ",
        "Due " to "শেষ তারিখ ",
        "Copied: " to "কপি করা হয়েছে: ",
        "Signed in as " to "সাইন ইন করা অ্যাকাউন্ট: ",
        "Recurring Income Due: " to "নিয়মিত আয় প্রাপ্য: ",
        "Recurring Bill Due: " to "নিয়মিত বিল পরিশোধের সময়: ",
        "Credit Card Bill Reminder: " to "ক্রেডিট কার্ড বিল রিমাইন্ডার: ",
        "Overdue Loan: " to "মেয়াদোত্তীর্ণ ঋণ: ",
        "Loan Due Today: " to "আজ ঋণের শেষ তারিখ: ",
        "Loan Due Tomorrow: " to "আগামীকাল ঋণের শেষ তারিখ: ",
        "Add to " to "যোগ করুন: ",
        "Withdraw from " to "উত্তোলন করুন: ",
        "Note: " to "নোট: ",
        "Reference: " to "রেফারেন্স: ",
        "Category: " to "ক্যাটাগরি: ",
        "Wallet: " to "ওয়ালেট: ",
        "Date: " to "তারিখ: ",
        "Period: " to "সময়কাল: ",
        "Contact: " to "যোগাযোগ: ",
        "Generated: " to "তৈরির সময়: ",
        "Adjust Current Balance (" to "বর্তমান ব্যালেন্স সমন্বয় (",
        "Reconciliation: " to "সমন্বয়: ",
        "Date of Giving Loan: " to "ধার দেওয়ার তারিখ: ",
        "Date of Taking Loan: " to "ধার নেওয়ার তারিখ: ",
        "Deadline: " to "পরিশোধের শেষ তারিখ: "
    )

    fun translate(text: String, lang: String = currentLanguage): String {
        if (!isBangla(lang) || text.isBlank()) return text

        val trimmed = text.trim()

        // Do not alter email addresses, URLs, or activation code placeholders
        if (trimmed.contains("@") && !trimmed.contains(" ")) return text
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return text
        if (trimmed == "XXXX-XXXX-XXXX" || trimmed.startsWith("PK3D-") || trimmed.startsWith("PKLT-")) return text

        // 1. Direct lookup in exactPhraseMap
        exactPhraseMap[trimmed.lowercase()]?.let { matched ->
            val leadingSpace = text.takeWhile { it.isWhitespace() }
            val trailingSpace = text.takeLastWhile { it.isWhitespace() }
            return "$leadingSpace$matched$trailingSpace"
        }

        var working = text

        // 2. Dynamic pattern replacements
        if (working.startsWith("Tap to upgrade to Pocket Premium", ignoreCase = true)) {
            working = working
                .replace("Tap to upgrade to Pocket Premium", "পকেট প্রিমিয়ামে আপগ্রেড করতে ট্যাপ করুন", ignoreCase = true)
                .replace("logs today", "টি লেনদেন আজ")
        }
        if (working.startsWith("Free tier: Choose any 3 default wallets", ignoreCase = true)) {
            working = working.replace("Free tier: Choose any 3 default wallets", "ফ্রি প্ল্যান: যেকোনো ৩টি ডিফল্ট ওয়ালেট নির্বাচন করুন", ignoreCase = true)
        }
        if (working.startsWith("Show more wallets", ignoreCase = true)) {
            working = working
                .replace("Show more wallets", "আরও ওয়ালেট দেখুন", ignoreCase = true)
                .replace("more)", "টি আরও)")
        }
        if (working.startsWith("Google Account Linked", ignoreCase = true)) {
            working = working.replace("Google Account Linked", "গুগল অ্যাকাউন্ট যুক্ত আছে", ignoreCase = true)
        }
        if (working.startsWith("Master monthly spending budget set to ", ignoreCase = true)) {
            working = working.replace("Master monthly spending budget set to ", "মাসিক মূল খরচের বাজেট নির্ধারণ করা হয়েছে: ", ignoreCase = true)
        }

        // Prefix replacements
        for ((enPrefix, bnPrefix) in prefixLabelReplacements) {
            if (working.startsWith(enPrefix, ignoreCase = true)) {
                val rest = working.substring(enPrefix.length)
                working = bnPrefix + translateSegment(rest, lang)
                break
            }
        }

        // Bullet-separated segments (e.g. "Lent • Principal ৳ 5,000 • Due 12 Oct")
        if (working.contains(" • ")) {
            working = working.split(" • ").joinToString(" • ") { seg ->
                translateSegment(seg, lang)
            }
        } else if (working.contains(" → ")) {
            working = working.split(" → ").joinToString(" → ") { seg ->
                translateSegment(seg, lang)
            }
        } else {
            working = translateCommonSubstrings(working)
        }

        return toBanglaDigits(working)
    }

    private fun translateSegment(segment: String, lang: String): String {
        val t = segment.trim()
        exactPhraseMap[t.lowercase()]?.let { return it }
        for ((enPrefix, bnPrefix) in prefixLabelReplacements) {
            if (t.startsWith(enPrefix, ignoreCase = true)) {
                return bnPrefix + translateCommonSubstrings(t.substring(enPrefix.length))
            }
        }
        return translateCommonSubstrings(t)
    }

    private fun translateCommonSubstrings(input: String): String {
        var s = input
        // Replace standalone default wallet/category names when surrounded by parentheses or colons
        exactPhraseMap[s.trim().lowercase()]?.let { return it }

        for ((enMonth, bnMonth) in monthReplacements) {
            if (s.contains(enMonth)) {
                s = s.replace(Regex("\\b$enMonth\\b"), bnMonth)
            }
        }
        for ((enDay, bnDay) in weekdayReplacements) {
            if (s.contains(enDay)) {
                s = s.replace(Regex("\\b$enDay\\b"), bnDay)
            }
        }
        s = s
            .replace(Regex("\\bAM\\b"), "পূর্বাহ্ণ")
            .replace(Regex("\\bPM\\b"), "অপরাহ্ণ")
            .replace(Regex("\\bam\\b"), "পূর্বাহ্ণ")
            .replace(Regex("\\bpm\\b"), "অপরাহ্ণ")
            .replace("/mo", "/মাস")
            .replace("/day", "/দিন")
            .replace(" Wallets", "টি ওয়ালেট")
            .replace(" wallets", "টি ওয়ালেট")
            .replace(" Transactions", "টি লেনদেন")
            .replace(" transactions", "টি লেনদেন")
            .replace(" entries)", "টি লেনদেন)")
            .replace(" entries", "টি লেনদেন")
            .replace(" days left", " দিন বাকি")
            .replace(" days ago", " দিন আগে")
            .replace(" days", " দিন")
            .replace("(Fixed subscription)", "(নির্ধারিত সাবস্ক্রিপশন)")
            .replace("Tap Pay to choose a wallet & log, or Remind Tomorrow.", "ওয়ালেট নির্বাচন করে পরিশোধ করতে ট্যাপ করুন, অথবা আগামীকাল মনে করিয়ে দিন।")
            .replace("Due TODAY", "আজ পরিশোধের দিন")
            .replace("Due TOMORROW", "আগামীকাল পরিশোধের দিন")
            .replace("Utilization:", "ব্যবহার:")
            .replace("Over budget by ", "বাজেট অতিক্রম করেছে: ")
            .replace(" · ARCHIVED", " · আর্কাইভ করা")
        return s
    }
}
