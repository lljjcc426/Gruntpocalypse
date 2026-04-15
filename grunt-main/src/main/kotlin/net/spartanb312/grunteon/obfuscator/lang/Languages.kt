package net.spartanb312.grunteon.obfuscator.lang

import net.spartanb312.grunteon.obfuscator.util.interfaces.DisplayEnum

enum class Languages(override val displayName: CharSequence, val code: String) : DisplayEnum {
    Descriptor("Descriptor", "desc"),
    Arabic("العربية", "ar-AE"),
    ChineseCN("简体中文(中国大陆)", "zh-CN"),
    ChineseHK("繁體中文(香港特別行政區)", "zh-HK"),
    ChineseTW("繁體中文(台灣)", "zh-TW"),
    Danish("dansk", "da-DK"),
    Dutch("Nederlands", "nl_NL"),
    English("English", "en-UK"),
    Finnish("suomi", "fi_FI"),
    French("French", "fr-FR"),
    German("Deutsch", "de-DE"),
    Greek("Greek", "el_GR"),
    Hindi("हिन्दी", "hi_IN"),
    Hungarian("magyar", "hu_HU"),
    Indonesian("Bahasa Indonesia", "id_ID"),
    Italian("italiano", "it_IT"),
    Japanese("日本語", "ja_JP"),
    Korean("한국어", "ko_KR"),
    Malay("Bahasa Melayu", "ms_MY"),
    Norwegian("norsk", "no_NO"),
    Polish("polski", "pl_PL"),
    PortugueseBR("português (Brazil)", "pt-BR"),
    PortuguesePT("português (Portugal)", "pt_PT"),
    Romanian("română", "ro_RO"),
    Russian("русский", "ru_RU"),
    Spanish("español", "es_ES"),
    Swedish("svenska", "sv_SE"),
    Thai("ไทย", "th_TH"),
    Turkish("Türkçe", "tr_TR"),
    Ukrainian("українська", "uk_UA"),
    Vietnamese("Tiếng Việt", "vi_VN"),
}

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class I18NDescriptorPath(val value: String)

fun enText(descriptor: String, value: String): MultiText = MultiText(descriptor).addLang(Languages.English, value).reg()

fun MultiText.ar(value: String): MultiText = addLang(Languages.Arabic, value)

fun MultiText.cn(value: String): MultiText = addLang(Languages.ChineseCN, value)

fun MultiText.hk(value: String): MultiText = addLang(Languages.ChineseHK, value)

fun MultiText.tw(value: String): MultiText = addLang(Languages.ChineseTW, value)

fun MultiText.da(value: String): MultiText = addLang(Languages.Danish, value)

fun MultiText.nl(value: String): MultiText = addLang(Languages.Dutch, value)

fun MultiText.en(value: String): MultiText = addLang(Languages.English, value)

fun MultiText.fi(value: String): MultiText = addLang(Languages.Finnish, value)

fun MultiText.fr(value: String): MultiText = addLang(Languages.French, value)

fun MultiText.de(value: String): MultiText = addLang(Languages.German, value)

fun MultiText.el(value: String): MultiText = addLang(Languages.Greek, value)

fun MultiText.hi(value: String): MultiText = addLang(Languages.Hindi, value)

fun MultiText.hu(value: String): MultiText = addLang(Languages.Hungarian, value)

fun MultiText.id(value: String): MultiText = addLang(Languages.Indonesian, value)

fun MultiText.it(value: String): MultiText = addLang(Languages.Italian, value)

fun MultiText.jp(value: String): MultiText = addLang(Languages.Japanese, value)

fun MultiText.kr(value: String): MultiText = addLang(Languages.Korean, value)

fun MultiText.ms(value: String): MultiText = addLang(Languages.Malay, value)

fun MultiText.no(value: String): MultiText = addLang(Languages.Norwegian, value)

fun MultiText.pl(value: String): MultiText = addLang(Languages.Polish, value)

fun MultiText.br(value: String): MultiText = addLang(Languages.PortugueseBR, value)

fun MultiText.pt(value: String): MultiText = addLang(Languages.PortuguesePT, value)

fun MultiText.ro(value: String): MultiText = addLang(Languages.Romanian, value)

fun MultiText.ru(value: String): MultiText = addLang(Languages.Russian, value)

fun MultiText.es(value: String): MultiText = addLang(Languages.Spanish, value)

fun MultiText.sv(value: String): MultiText = addLang(Languages.Swedish, value)

fun MultiText.th(value: String): MultiText = addLang(Languages.Thai, value)

fun MultiText.tr(value: String): MultiText = addLang(Languages.Turkish, value)

fun MultiText.uk(value: String): MultiText = addLang(Languages.Ukrainian, value)

fun MultiText.vi(value: String): MultiText = addLang(Languages.Vietnamese, value)