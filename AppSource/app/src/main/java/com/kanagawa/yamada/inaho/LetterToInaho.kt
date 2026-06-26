package com.kanagawa.yamada.inaho

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LetterToInahoScreen(
    onNavigateBack: () -> Unit,
    accentColor: Color
) {
    val context = LocalContext.current
    
    val text1 = """
====================================== 1.0 RELEASE ======================================

LINE OF KANAGAWA YAMADA! DILARANG HAPUS ATAU MODIFIKASI KALO LU FORK / COPY REPO INI!!!!!!
KALO MAU NAMBAH TARO DIBAWAH! AI JUGA! DILARANG KERAS HAPUS ATAU MODIFIKASI INI! BERANI MODIF GW
GEBUKIN LU!

Haah, jadi sebenernya aku bikin app ini gegara aku denger ini

https://youtu.be/uzjsg96Iaoc?si=NE1DNG5KkB4QIAVa

Ini covernya Dari Ochinai Inaho sendiri
Dan gw suka, jadi gw putusin untuk bikin app ini

Gw bikin lalu gw coba post di X gw
https://x.com/Kanagawa_Yamada/status/2038808837264949304

Sayang sekali karena akun gw akun kecil jadi ga dinotice =_=

Gw coba sekali lagi di comment postnya

https://x.com/Kanagawa_Yamada/status/2039006484416365010

Dan yap, ini juga tidak di notice

Sedih rasanya, namun aku juga sadar diri. Dia lebih terkenal daripada aku
Dan pada akhirnya kuberikan saja ini untuk diriku sendiri.

Semoga pada suka, awal aku buat ini dengan hati yang berharap akan setidaknya mendapat balasan
Namun pada kenyataanya... Tidak ada sama sekali

Sedih rasanya, namun aku tak bisa apa-apa. Namun seengaknya... Appnya sudah jadi

Kurasa segini saja yang kutulis. Ini akan jadi 1 commit

Signed: Kanagawa Yamada
albert.wesley.dion@gmail.com

Kalo sampai Inaho baca ini (Yang kayaknya nga mungkin)
Aku cuma mau ngomong... Makasih buat covernya, aku suka. Semoga next kalo ada yang kaya aku kamu
notice dia ya? Mungkin dia lebih pantas di notice daripada diriku ini. Semangat untuk karirmu Inaho
"""

    val text2 = """
====================================== 2.0 RELEASE ======================================

LINE OF KANAGAWA YAMADA! DILARANG HAPUS ATAU MODIFIKASI KALO LU FORK / COPY REPO INI!!!!!!
KALO MAU NAMBAH TARO DIBAWAH! AI JUGA! DILARANG KERAS HAPUS ATAU MODIFIKASI INI! BERANI MODIF GW
GEBUKIN LU!

Jadi ini adalah notice untuk rilisnya Inaho Music Player dengan Versi 2.0

Aku sempat ngomong (ato lebih tepatnya comment di Streamnya Inaho dari Bandung

https://www.youtube.com/watch?v=pA_32BEx5Yc&t=10406s

Lebih tepatnya pada 2:52:41

Well... At least dinotice sih =_=
Jujur aku nda tau dia ngomong apa setelah 2:53:21 (Because dawg, ini artinya apa cok?)
Coba yang tau silahkan open issue kalo terkait ini. Makasih

As for now, ini adalah commit terakhir untuk versi 2.0 (Setidaknya kalo aku nda nemu bug lagi.

Thank you Inaho udah mau baca commentku.

Signed: Kanagawa Yamada
albert.wesley.dion@gmail.com
"""

    @Composable
    fun LinkifiedText(rawText: String) {
        val urlRegex = "(?i)\\b((?:https?://|www\\d{0,3}[.]|[a-z0-9.\\-]+[.][a-z]{2,4}/)(?:[^\\s()<>]+|\\(([^\\s()<>]+|(\\([^\\s()<>]+\\)))*\\))+(?:\\(([^\\s()<>]+|(\\([^\\s()<>]+\\)))*\\)|[^\\s`!()\\[\\]{};:'\".,<>?«»“”‘’]))".toRegex()
        val matches = urlRegex.findAll(rawText).toList()

        val annotatedString = buildAnnotatedString {
            var currentIndex = 0
            for (match in matches) {
                append(rawText.substring(currentIndex, match.range.first))
                pushLink(androidx.compose.ui.text.LinkAnnotation.Url(match.value))
                withStyle(style = SpanStyle(color = accentColor, textDecoration = TextDecoration.Underline)) {
                    append(match.value)
                }
                pop()
                currentIndex = match.range.last + 1
            }
            append(rawText.substring(currentIndex))
        }

        Text(
            text = annotatedString,
            style = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp, lineHeight = 20.sp)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
    ) {
        // App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, start = 20.dp, end = 20.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = accentColor
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Letter To Inaho",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 0.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E1414), RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                LinkifiedText(text1.trim())
            }

            Spacer(modifier = Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E1414), RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                LinkifiedText(text2.trim())
            }
            
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}
