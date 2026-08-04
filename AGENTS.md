# MASTER BLUEPRINT & REGULASI PENGEMBANGAN: YANSPROJECT.ID ERP SYSTEM

> **DOKUMEN INDUK REGULASI & BLUEPRINT PENGEMBANGAN APLIKASI**  
> *Versi: 1.3.1 (Master Release Update - Security, Official Identity & Cloud Sync Optimization)*  
> *Sistem: YANSPROJECT.ID Enterprise Resource Planning & Financial Management System*  
> *Status: AKTIF & MENJADI ACUAN MUTLAK KODE BASE*  
> *Referensi Utama: `/GRAND_MASTER_BLUEPRINT.md`*

---

## 1. PROLOG & IDENTITAS RESMI PERUSAHAAN

**YANSPROJECT.ID** adalah platform Enterprise Resource Planning (ERP) & Manajemen Keuangan Cyber-Industrial tingkat tinggi yang dirancang khusus untuk manajemen proyek cetak, manufaktur custom, persediaan stok, faktur penagihan, serta histori transaksi multi-channel secara real-time.

### 1.1 Standard Identitas Resmi (Official Identity Data)
Setiap dokumen, invoice, cetak thermal, PDF/PNG export, footer WhatsApp, dan layar pengaturan **WAJIB MENGGUNAKAN DATA RESMI BERIKUT**:
- **Company Name**: `YANSPROJECT.ID`
- **Support Email**: `yansart31@gmail.com`
- **Support WhatsApp**: `+62 877-7739-8813`
- **Address / Alamat**: `Tangerang, Banten`
- **Tagline**: `Luxury Visual Identity & Custom Merch`

Seluruh pembaruan, penambahan fitur, bugfix, dan modifikasi kode pada aplikasi ini **WAJIB MENGIKUTI DENGAN PATUH** setiap spesifikasi, DNA warna, hierarki visual, dan aturan arsitektur yang tertera di dalam dokumen Master Blueprint ini.

---

## 2. DNA PALET WARNA & SISTEM TEMA (THEME COLOR PALETTE)

Sistem menggunakan tema **Cyber Emerald & High-Contrast Industrial Dark Canvas**. Warna tidak boleh dipilih secara acak; harus selalu mereferensikan konstanta warna resmi di `YansDesignSystem.kt` atau `Color.kt`.

### 2.1 Color Specifications
| Nama Variable | Code Color (Hex) | Fungsi Utama & Hierarki Visual |
| :--- | :--- | :--- |
| `ShadowBlack` | `#071213` (`0xFF071213`) | Canvas latar belakang utama aplikasi (Ultra-dark cyan black). |
| `DeepCanvas` | `#0B1B1C` (`0xFF0B1B1C`) | Latar belakang modul/container tingkat 2. |
| `DeepTeal` | `#163536` (`0xFF163536`) | Warna dasar tombol, card container, dan surface elemen UI. |
| `CyberEmerald` | `#0F433F` (`0xFF0F433F`) | Dark emerald accent untuk status active dan container terfokus. |
| `AgedGold` | `#C8A25D` (`0xFFC8A25D`) | **Aksen Kemewahan / Primary Brand**: Header teks, border premium, highlight angka utama. |
| `GoldMuted` | `#8C7240` (`0xFF8C7240`) | Sub-header, label deskripsi sekunder, dan garis pembatas halus. |
| `AlertGreen` | `#36D0A7` (`0xFF36D0A7`) | **Status Positif**: Pemasukan (Inflow), Lunas, Stok Aman, Online Status Indicator. |
| `AlertRed` | `#FF5252` (`0xFFFF5252`) | **Status Kritis**: Pengeluaran (Expense), Piutang Kritis, Badge Notifikasi, Hapus. |
| `StatusWarningGold` | `#FFC107` (`0xFFFFC107`) | **Status Peringatan**: DP/Piutang berjalan, Stok Tipis, Antrean Proyek. |
| `HighlightSoftCyan` | `#4FD1C5` (`0xFF4FD1C5`) | Aksen data sekunder, grafik, status pengerjaan, dan elemen interaktif. |
| `TextLight` | `#E2E8F0` (`0xFFE2E8F0`) | Teks utama pada container gelap agar keterbacaan (readability) 100% optimal. |
| `TextMuted` | `#94A3B8` (`0xFF94A3B8`) | Placeholder, caption kecil, dan informasi pendukung. |

### 2.2 Aturan Anti-Blur & Card Surface Integrity
- **Dilarang keras** menggunakan efek `radialGradient` atau `blur` berat berlebihan pada latar belakang card/container yang menyebabkan efek "bayangan kotor/buram" (blur artifacts).
- Seluruh Card & Glassmorphic Container harus menggunakan **Alpha Surface Solid** (misal: `Color(0x25163536)` atau `Color(0xFF0D1E1E)`) dengan garis tepi tegas (**Crisp Solid Border 0.8dp - 1.2dp**) dan corner radius yang konsisten (10.dp - 16.dp).

---

## 3. ATURAN UI/UX, COMPONENT OVERLAY, DAN PRESISI BADGE

### 3.1 Overlapping Badge & Notification Count Rule
1. **Presisi Bebas Terpotong (Zero Clipping)**:
   - Badge notifikasi (misalnya jumlah unread count `13` pada Bell Icon di Header) **TIDAK BOLEH TERPOTONG** oleh container induk.
   - Container induk tombol harus menggunakan `Box(modifier = Modifier.wrapContentSize())` agar elemen overlay seperti badge dapat mengudara di luar batas ikon dengan sempurna.
2. **Offset & Styling Presisi**:
   - Offset badge resmi: `offset(x = 5.dp, y = (-5).dp)`.
   - Latar belakang badge: `AlertRed` (`#FF5252`) dengan border melingkar tegas `1.dp` berwarna `#0D1E1E`.
   - Minimum size badge: `minWidth = 18.dp`, `minHeight = 18.dp` dengan padding `horizontal = 4.dp, vertical = 1.dp` agar angka 2–3 digit tetap muat dengan rapi.

---

## 4. HIERARKI OTORISASI ROLE (OWNER, ADMIN, MEMBER, NON-MEMBER)

1. **Owner**: Akses 100% global tanpa batasan (Kas, Ledger Keuangan, Member Management, Reset Database).
2. **Admin**: Otorisasi operasional proyek, stok, invoice, dan manajemen member.
3. **Member**: Hak akses terisolasi secara mandiri (hanya melihat Invoice & Riwayat milik sendiri via nama, email, WhatsApp), membaca Kitab Digital, dan update profil.
4. **Non-Member**: Katalog publik dan halaman login/registrasi.

---

## 5. MODUL UTAMA APLIKASI
1. **Dashboard & Sub-Ledger**: Pemasukan, Modal Awal/Berjalan, Pengeluaran, Rekap Kas/Bank, Gross/Net Profit, Penjualan & Piutang.
2. **Custom Project**: Matriks Apparel Pricing (Size x Sleeve), Tracking DP, Material Costing, Workflow status (`Planning` -> `Production` -> `Ready` -> `Completed` -> `Delivered`).
3. **Stok**: Master catalog, varian warna/ukuran/lengan, inventory ledger, auto-summary trigger.
4. **Invoice**: Penomoran otomatis (`INV/2026/AJB/...`), Direct & Custom Mode, multi-payment tracking, PDF/CSV export, deduplikasi otomatis.
5. **Riwayat Transaksi**: Timelines pergerakan stok, inflow, outflow, cicilan invoice, filter multi-periode, isolasi data member.
6. **Settings**: Profil usaha, parameter PPN/prefix, manajemen anggota, system health/backup.
7. **Kitab Digital**: Dokumentasi SOP, regulasi internal, dan panduan industri.
8. **Customer Selection Section**: Auto-lookup member terdaftar, chip cepat, deteksi Tier & WhatsApp, serta histori non-member.

---

## 6. FIREBASE CLOUD REALTIME SYNC & OPTIMASI EFISIENSI
1. **Offline-First Room Database**: Seluruh operasi simpan/update/hapus mengeksekusi Room SQLite secara langsung (0ms latency), lalu menyinkronkan ke Firestore secara asinkron via `Dispatchers.IO`.
2. **Efisiensi Cloud Listener**: Realtime listener Firestore hanya aktif saat screen berstatus active/foreground untuk menghemat quota read & daya baterai.
3. **Rekonsiliasi & Anti-Duplikasi State**: Sinkronisasi reaktif antar-halaman (Single-Source-of-Truth) via Room Flow untuk memastikan data di Dashboard, Sub-Ledger, Invoice, dan Project selalu identik secara real-time.

---

## 7. REGULASI PENGEMBANGAN & ATURAN INTEGRITAS (DEVELOPER MANDATES)

1. **DATA RESMI PERUSAHAAN**: Jangan pernah menggunakan alamat, email, atau kontak fiktif/placeholder. Selalu referensikan `BusinessIdentityProvider` dan `AppSettings`.
2. **JANGAN PERNAH** merusak atau mengubah skema warna dasar `AgedGold`, `ShadowBlack`, `DeepTeal`, `AlertGreen`, dan `AlertRed`.
3. **JANGAN PERNAH** menambahkan kembali efek `radialGradient` kotor atau background blur yang menutupi konten visual.
4. **WAJIB MENJAGA** presisi overlay badge agar selalu tampil utuh di luar batas kotak ikon tanpa terpotong (zero clipping).
5. **CLEAN LEDGER DISPLAY**: Pada detail tampilan Ledger dan pembayaran Invoice, hapus keterangan "Operator" / "Oleh" serta bagian "Jam/HH:mm" yang tidak valid, cukup tampilkan tanggal bersih (`dd MMMM yyyy`).
6. **SETIAP MODUL BARU** harus menggunakan komponen standar M3 yang sudah disesuaikan dengan `YansDesignSystem.kt` dan mendukung responsive scaling.
7. **VERIFIKASI KOMPILASI**: Setiap perubahan kode harus terverifikasi sukses melalui `compile_applet` tanpa warning fatal atau break pada build script.

---
*YANSPROJECT.ID ERP — High-Precision Engineering & Industrial Financial Intelligence.*
