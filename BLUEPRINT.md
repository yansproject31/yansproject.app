# MASTER BLUEPRINT: YANSPROJECT.ID ERP SYSTEM

> **DOKUMEN INDUK REGULASI & BLUEPRINT PENGEMBANGAN APLIKASI**  
> *Versi: 2.0 (Master Release)*  
> *Sistem: YANSPROJECT.ID Enterprise Resource Planning & Financial Management System*  
> *Status: AKTIF & MENJADI ACUAN MUTLAK KODE BASE*

---

## 1. PROLOG & IDENTITAS SISTEM

**YANSPROJECT.ID** adalah platform Enterprise Resource Planning (ERP) & Manajemen Keuangan Cyber-Industrial tingkat tinggi yang dirancang khusus untuk manajemen proyek cetak, manufaktur custom, persediaan stok, faktur penagihan, serta histori transaksi multi-channel secara real-time.

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

### 3.2 Lottie Micro-Interactions & Transitions
- Transisi antar tab utama menggunakan mikro-animasi Lottie yang tidak mengganggu (`SubtleLottieTabTransition` & `LottieTabPulse`).
- Filter Chip aktif menyertakan indikator halus `LottieFilterChipPulse` (size 12.dp) untuk memberikan feedback visual profesional.

---

## 4. HIERARKI VISUAL & TYPOGRAPHY SYSTEM

### 4.1 Typography Standards
- **Brand Title / Header Utama**: `FontWeight.ExtraBold`, Letter Spacing `1.sp` – `2.sp`, Warna `AgedGold` atau `TextLight`.
- **Section Headers (misal: "BUSINESS ALERTS & SYSTEM STATUS")**: `FontSize = 11.sp` - `12.sp`, `FontWeight.Bold`, Uppercase, Letter Spacing `1.sp`, Warna `AgedGold`.
- **Primary Numerical Metrics (misal: Omset, Total Piutang)**: `FontSize = 16.sp` - `22.sp`, `FontWeight.ExtraBold`, High contrast color (`AgedGold`, `AlertGreen`, or `HighlightSoftCyan`).
- **Body & Subtitle**: `FontSize = 12.sp` - `14.sp`, `FontWeight.Medium` / `Regular`, Warna `TextLight` atau `TextMuted`.

### 4.2 Spacing & Grid System
- Grid dasar menggunakan kelipatan **4dp / 8dp**:
  - Small Spacing: `4.dp` / `6.dp`
  - Medium Padding: `8.dp` / `12.dp`
  - Container / Screen Margin: `16.dp` / `20.dp`
- Standard Touch Target: Minimal **48.dp x 48.dp** untuk seluruh tombol dan icon interaktif.

---

## 5. MODUL UTAMA & ARSITEKTUR FITUR ERP

### 5.1 Main Navigation Header & Scaffold (`MainScaffold.kt`)
- **Top Header Bar**: Logo Brand `YANSPROJECT.ID`, Status Pill `ONLINE` / `OFFLINE`, Search Button, Notification Bell with Overlaid Count Badge, Quick Settings Button, Logout Action.
- **Bottom Navigation Bar (`BottomNavigationBar.kt`)**: 5 Tab Utama:
  1. `DASHBOARD` (Ikhtisar Keuangan, Inflow, Outflow, Alert Bisnis)
  2. `PROJECT` (Manajemen Pengerjaan Pesanan, Costing Material, Progress)
  3. `STOK` (Katalog Inventaris, Peringatan Stok Tipis, In/Out Stock)
  4. `INVOICE` (Pembuatan Faktur, Tracking DP & Piutang, Cetak/Export PDF)
  5. `RIWAYAT` (Auditing Transaksi Lengkap, Filter Multi-Periode, Download CSV/PDF)

### 5.2 Dashboard & Financial Intelligence Engine (`DashboardScreen.kt`)
- Filter Periode Universal: `Semua`, `Hari Ini`, `Minggu Ini`, `Bulan Ini`, `Tahun Ini`.
- Metric Cards: Total Omset, Diterima (Cash Inflow), Sisa Piutang, Volume Produk.
- Real-time Business Alerts: Otomatis mendeteksi stok di bawah batas minimum, invoice jatuh tempo, dan proyek mendekati deadline.

### 5.3 Modul Riwayat Transaksi (`RiwayatScreen.kt`)
- Mengonsolidasikan seluruh aliran data ERP:
  - Perubahan/Pergerakan Stok
  - Pembayaran Proyek (DP / Pelunasan)
  - Pemasukan Langsung (Direct Inflows)
  - Pengeluaran Operasional (Expenses)
- Menyediakan fitur pencarian cepat & Export Rangkuam Laporan PDF & CSV.

### 5.4 Synchronisation & Persistence Layer (`MainViewModel.kt` & Firestore)
- Penyimpanan lokal berbasis Room Database & SharedPreferences.
- Integrasi Cloud Real-time Firestore untuk sinkronisasi multi-device & multi-user role.
- Proteksi pencegahan infinite loop sync storm pada pengirim/penerima notifikasi.

---

## 6. REGULASI PENGEMBANGAN & ATURAN INTEGRITAS (DEVELOPER MANDATES)

Setiap pengembang atau AI Agent yang memperbarui basis kode aplikasi ini **WAJIB DIPATUHI PERATURAN BERIKUT**:

1. **JANGAN PERNAH** merusak atau mengubah skema warna dasar `AgedGold`, `ShadowBlack`, `DeepTeal`, `AlertGreen`, dan `AlertRed`.
2. **JANGAN PERNAH** menambahkan kembali efek `radialGradient` kotor atau background blur yang menutupi konten visual.
3. **WAJIB MENJAGA** presisi overlay badge agar selalu tampil utuh di luar batas kotak ikon tanpa terpotong (zero clipping).
4. **SETIAP MODUL BARU** harus menggunakan komponen standar M3 yang sudah disesuaikan dengan `YansDesignSystem.kt` dan mendukung responsive scaling.
5. **VERIFIKASI KOMPILASI**: Setiap perubahan kode harus terverifikasi sukses melalui `compile_applet` tanpa warning fatal atau break pada build script.

---
*YANSPROJECT.ID ERP — High-Precision Engineering & Industrial Financial Intelligence.*
