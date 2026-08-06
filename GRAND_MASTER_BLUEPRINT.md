# GRAND MASTER BLUEPRINT: YANSPROJECT.ID ENTERPRISE ERP SYSTEM

> **DOKUMEN INDUK REGULASI, ARSITEKTUR, TEMA, & SPESIFIKASI PENGEMBANGAN APLIKASI**  
> *Sistem: YANSPROJECT.ID Enterprise Resource Planning (ERP) & Financial Intelligence System*  
> *Versi: 1.3.3 (Grand Master Release Update - Zero-Crash Update, Single Source of Truth & Enterprise Data Sync)*  
> *Status: AKTIF, MENJADI ACUAN MUTLAK & HUKUM TERTINGGI BASIS KODE*

---

## 1. PROLOG & IDENTITAS RESMI PERUSAHAAN

**YANSPROJECT.ID** adalah platform *Enterprise Resource Planning* (ERP) dan Manajemen Keuangan Cyber-Industrial tingkat tinggi yang dirancang khusus untuk mengelola:
1. **Manufaktur Custom Apparel & Konveksi**: Pakaian, Seragam, Printing, Sablon, Matriks Ukuran (Size x Sleeve Costing).
2. **Manajemen Proyek & Cetak Industrial**: Custom Project Workflow dari Perencanaan, Produksi, Siap Kirim, hingga Selesai.
3. **Persediaan Stok Bahan & Produk Jadi**: Master Catalog, Varian Warna/Ukuran/Lengan, & Inventory Ledger.
4. **Penagihan & Pembayaran Multi-Payment**: Dual Invoice Engine (Direct Stock Sale & Custom Project), Uang Muka (DP), Cicilan, Pelunasan.
5. **Konsolidasi Arus Kas & Keuangan Global**: Cash Inflow, Cash Outflow, Sub-Ledger Kas/Bank, Profitability Analysis (Gross/Net Profit).
6. **Sistem Akses Terisolasi Multi-Role**: Role Owner, Admin, Member, dan Non-Member / Public.

### 1.1 Standard Identitas Resmi (Official Business Identity)
Seluruh cetakan thermal, ekspor PDF/PNG, footer WhatsApp, watermark invoice, dan layar profil/setelan **WAJIB MENGIKUTI STANDAR RESMI**:
- **Company Name**: `YANSPROJECT.ID`
- **Support Email**: `yansart31@gmail.com`
- **Support WhatsApp**: `+62 877-7739-8813`
- **Address / Alamat**: `Tangerang, Banten`
- **Tagline**: `Luxury Visual Identity & Custom Merch`

Seluruh pembaruan, penambahan fitur, pembenahan bug (*bugfix*), *refactoring*, dan modifikasi kode pada aplikasi ini **WAJIB MENGIKUTI DENGAN PATUH DAN KETAT** setiap spesifikasi, DNA warna, hierarki visual, arsitektur otorisasi, dan aturan pengembangan yang tertera di dalam dokumen Grand Master Blueprint ini.

---

## 2. DNA PALET WARNA & SISTEM TEMA (THEME COLOR PALETTE)

Sistem menggunakan tema **Cyber Emerald & High-Contrast Industrial Dark Canvas**. Warna tidak boleh dipilih secara acak; harus selalu mereferensikan konstanta warna resmi di `YansDesignSystem.kt` atau `Color.kt`.

### 2.1 Spesifikasi Warna Resmi (Color Palette)

| Nama Variable | Code Color (Hex) | Fungsi Utama & Hierarki Visual |
| :--- | :--- | :--- |
| `ShadowBlack` / `DarkTealBase` | `#071516` / `#071213` | Canvas latar belakang utama aplikasi (Ultra-dark cyan black). |
| `DeepCanvas` / `DarkTealSurface` | `#0F2E2F` / `#0B1B1C` | Latar belakang modul/container tingkat 2 & surface section. |
| `PrimaryDarkTeal` / `DeepTeal` | `#0D3738` / `#163536` | Warna dasar tombol, card container, dan surface elemen UI. |
| `CyberEmerald` | `#0F433F` | Dark emerald accent untuk status active dan container terfokus. |
| `AgedGold` / `AccentAgedGold` | `#C6A15B` / `#C8A25D` | **Aksen Kemewahan / Primary Brand**: Header teks, border premium, highlight angka utama. |
| `GoldMuted` | `#8C7240` | Sub-header, label deskripsi sekunder, dan garis pembatas halus. |
| `AlertGreen` / `YansSuccess` | `#30D158` / `#36D0A7` | **Status Positif**: Pemasukan (Inflow), Lunas, Stok Aman, Online Status Indicator. |
| `AlertRed` / `YansError` | `#FF5A5A` / `#FF5252` | **Status Kritis**: Pengeluaran (Expense), Piutang Kritis, Badge Notifikasi, Action Hapus. |
| `StatusWarningGold` / `AmberWarning` | `#FFB300` / `#FFC107` | **Status Peringatan**: DP/Piutang berjalan, Stok Tipis, Antrean Proyek. |
| `HighlightSoftCyan` / `NeonCyan` | `#4FD1C5` | Aksen data sekunder, grafik, status pengerjaan, dan elemen interaktif. |
| `TextLight` / `YansTextPrimary` | `#FFFFFF` / `#E2E8F0` | Teks utama pada container gelap agar keterbacaan (*readability*) 100% optimal. |
| `TextMuted` / `YansTextSecondary` | `#A7B8B3` / `#94A3B8` | Placeholder, caption kecil, dan informasi pendukung. |

### 2.2 Aturan Surface Integrity & Anti-Blur
- **Dilarang keras** menggunakan efek `radialGradient` kotor atau `blur` berat berlebihan pada latar belakang card/container yang menyebabkan efek "bayangan buram" (*blur artifacts*).
- Seluruh Card & Glassmorphic Container wajib menggunakan **Alpha Surface Solid** (seperti `Color(0x25163536)` atau `Color(0xFF0D1E1E)`) dengan garis tepi tegas (**Crisp Solid Border 0.8dp - 1.2dp**) dan corner radius yang konsisten (10.dp - 16.dp).

### 2.3 Aturan Overlapping Badge & Notification Count (Zero Clipping)
1. **Presisi Bebas Terpotong (Zero Clipping)**:
   - Badge notifikasi (misalnya jumlah unread count pada Bell Icon di Top Header Bar) **TIDAK BOLEH TERPOTONG** oleh container induk.
   - Container induk tombol harus menggunakan `Box(modifier = Modifier.wrapContentSize())` agar elemen overlay badge mengudara di luar batas ikon dengan sempurna.
2. **Offset & Styling Presisi**:
   - Offset badge resmi: `offset(x = 5.dp, y = (-5).dp)`.
   - Latar belakang badge: `AlertRed` (`#FF5252`) dengan border melingkar tegas `1.dp` berwarna `#0D1E1E`.
   - Minimum size badge: `minWidth = 18.dp`, `minHeight = 18.dp` dengan padding `horizontal = 4.dp, vertical = 1.dp` agar angka 2–3 digit tetap muat dengan rapi.

---

## 3. SISTEM PENGGUNA & OTORISASI (ROLE-BASED ACCESS CONTROL - RBAC)

Aplikasi memiliki 4 tingkatan hak akses pengguna yang mengatur secara tegas batasan data dan fitur yang dapat diakses:

### 3.1 Role OWNER (Pemilik & Super Admin)
- **Otorisasi**: Akses 100% penuh tanpa batas ke seluruh modul aplikasi.
- **Hak Khusus**:
  - Melihat seluruh ringkasan keuangan global (Kas, Profit, Margin, Modal Awal/Berjalan, Omset Total).
  - Melakukan approval/persetujuan transaksi & hapus data permanen.
  - Mengelola seluruh daftar anggota (*Member Management*) dan mengubah role pengguna.
  - Mengonfigurasi parameter keuangan (Prefix Invoice, Pajak PPN %, Metode Pembayaran, Rekening Bank).
  - Mengakses Laporan Audit Health System, Log Aktivitas, dan Reset Database.

### 3.2 Role ADMIN (Pengelola Operasional)
- **Otorisasi**: Pengelolaan harian operasional proyek, penjualan stok, pencatatan transaksi, dan pembaharuan status.
- **Hak Khusus**:
  - Membuat & mengubah data Custom Project, Stock, dan Invoice.
  - Menginput pembayaran cicilan/DP dan mencetak dokumen faktur (PDF/CSV).
  - Melihat data keuangan operasional yang berkaitan langsung dengan transaksi harian.

### 3.3 Role MEMBER (Pelanggan / Anggota Terdaftar)
- **Otorisasi**: Akses terbatas yang terisolasi secara ketat khusus untuk data miliknya sendiri.
- **Isolasi Data Mandiri**:
  - Member hanya dapat melihat Invoice & Riwayat Transaksi yang cocok dengan **Nama Display**, **Email**, atau **Nomor WhatsApp** miliknya.
  - Tidak dapat melihat laporan keuangan global bisnis, kas perusahaan, profit margin, atau data pengguna lain.
  - Dapat membaca **Kitab Digital / Digital Book** yang dipublikasikan oleh Owner.
  - Mengelola profil pengguna mandiri (Ganti foto, WhatsApp, Alamat).
  - Mendapatkan Tier Harga Khusus Member (Price Category Tier).

### 3.4 Role NON-MEMBER / PUBLIC (Tamu)
- **Otorisasi**: Akses visual dasar sebelum otentikasi.
- **Fungsi**:
  - Membuka Katalog Produk Publik & Stok Bahan.
  - Mengajukan permintaan estimasi harga / order awal.
  - Halaman Login & Registrasi Akun Baru.

---

## 4. MODUL SELEKSI KUSTOMER & PELANGGAN (`CustomerSelectionSection.kt`)

Setiap form pembuatan proyek custom maupun invoice wajib memanfaatkan komponen standar `CustomerSelectionSection`:
1. **Quick Member Selection Chips**: Menampilkan chip pelanggan terdaftar dari `AppSettings.getMembers()`.
2. **Auto-Fill Details**: Saat chip member dipilih, sistem secara otomatis mengisi nama customer, nomor WhatsApp, alamat pengiriman, dan menampilkan badge **"AKUN MEMBER TERDAFTAR • Tier: [TierName]"**.
3. **Riwayat Non-Member Lookup**: Mengagregasi data kustomer histori dari riwayat proyek & invoice sebelumnya secara cerdas.
4. **Custom Input Fallback**: Memungkinkan input manual untuk pelanggan baru non-terdaftar tanpa memblokir alur pengerjaan.

---

## 5. MODUL UTAMA & ARSITEKTUR FITUR ERP

### 5.1 Main Navigation Bar & Header (`MainScaffold.kt` & `BottomNavigationBar.kt`)
- **Top Header Bar**:
  - Logo Utama `YANSPROJECT.ID` dengan aksen `AgedGold`.
  - Status Indicator Pill: `ONLINE` (`AlertGreen`) atau `OFFLINE` (`AlertRed`).
  - Universal Search Button untuk pencarian cepat.
  - Bell Icon untuk Notifikasi Sistem dengan Overlaid Count Badge (Zero Clipping Offset: `x = 5.dp, y = (-5).dp`).
  - Quick Settings Button & Logout Action.
- **Bottom Navigation Bar (5 Tab Utama)**:
  1. `DASHBOARD`: Ikhtisar Keuangan, Cash Inflow, Outflow, Alert Bisnis, & Sub-Ledger Keuangan.
  2. `PROJECT`: Manajemen Pengerjaan Pesanan Custom, Matrix Costing Apparel, Progress Pengerjaan.
  3. `STOK`: Katalog Inventaris Produk & Bahan, Peringatan Stok Tipis, In/Out Stock Ledger.
  4. `INVOICE`: Pembuatan Faktur Penagihan (Direct & Custom), Tracking DP & Cicilan, Cetak/Export PDF.
  5. `RIWAYAT`: Auditing Transaksi Lengkap, Filter Multi-Periode, Download Laporan PDF & CSV.

### 5.2 Dashboard & Financial Intelligence Engine (`DashboardScreen.kt`, `LedgerScreens.kt`, `CashFlowScreen.kt`)
- **Universal Period Filter**: `Semua`, `Hari Ini`, `Minggu Ini`, `Bulan Ini`, `Tahun Ini`.
- **Metric Cards Utama**:
  - Total Omset Penjualan (`AgedGold`).
  - Cash Inflow / Diterima (`AlertGreen`).
  - Total Piutang / Belum Lunas (`AlertRed` / `StatusWarningGold`).
  - Total Volume Produk / Pesanan (`HighlightSoftCyan`).
- **Real-time Business Alerts**:
  - Deteksi otomatis stok bahan/produk di bawah batas minimum safety stock.
  - Deteksi otomatis invoice jatuh tempo & piutang tertunda.
  - Deteksi deadline proyek custom mendekati batas pengiriman.
- **Financial Sub-Ledger Screens**:
  - Pemasukan Direct & Inflow Kas.
  - Modal Awal & Modal Berjalan.
  - Pengeluaran Operasional (*Expense Categories*: Produksi & Aksesoris, Transport, Operasional, Lainnya).
  - Rekap Kas & Bank.
  - Hitungan Net Profit & Gross Profit.
  - Penjualan & Piutang Berjalan.
- **Regulasi Tampilan Detail Ledger (Detail Presentation Cleanliness)**:
  - Pada seluruh dialog/halaman detail Ledger dan riwayat pembayaran Invoice, **DIHARUSKAN UNTUK TIDAK MENAMPILKAN** keterangan "Operator", "Oleh: Owner/Lainnya", maupun bagian "Jam / HH:mm" yang sering kali tidak valid.
  - Tampilan tanggal transaksi **HARUS BERSIH** hanya menggunakan format tanggal (`dd MMMM yyyy` atau `dd/MM/yyyy`).

### 5.3 Custom Project & Apparel Matrix Pricing (`ProjectScreen.kt`, `CustomProjectFormScreen.kt`, `CustomProjectViewModel.kt`)
- **Apparel Matrix Costing**:
  - Mendukung input matriks kombinasi ukuran (XS, S, M, L, XL, XXL, 3XL, Custom) dan tipe lengan (Pendek, Panjang, Raglan, Custom).
  - Kalkulasi otomatis total kuantitas, harga per pcs, diskon nominal/persen, pajak PPN %, dan Grand Total.
  - Penanganan Biaya Bahan / Material Costing secara langsung.
- **Tracking DP & Pelunasan**:
  - Pencatatan pembayaran awal (Uang Muka/DP) dengan verifikasi otomatis status `DP PRODUKSI` atau `LUNAS`.
- **Workflow Status Proyek**:
  - Flow berurutan: `Planning` -> `Production` -> `Ready` -> `Completed` -> `Delivered`.
  - Batching Produksi & Alokasi Material dari Stok.

### 5.4 Stok & Inventaris Management (`StockScreen.kt` & `StockManagerViewModel.kt`)
- **Master Catalog & Varian**:
  - Pengelompokan hirarki: Katalog Seri/Produk -> Varian (Warna, Ukuran, Tipe Lengan) -> Inventory Ledger.
- **Inventory Ledger & History**:
  - Tracking pergerakan barang (Stok Masuk, Stok Keluar, Retur, Penyesuaian Stock Opname) lengkap dengan catatan transaksi.
- **Auto Stock Summary Trigger**:
  - Setiap perubahan pada varian stok secara otomatis memperbarui ringkasan total stok pada katalog induk.
- **Peringatan Stok Tipis (Safety Stock Limit)**:
  - Indikator otomatis warna merah/kuning ketika kuantitas barang mencapai batas minimum.

### 5.5 Invoice & Dual Invoice Manager (`InvoiceScreen.kt`, `DualInvoiceDashboardScreen.kt`, `DualInvoiceEditorScreen.kt`, `DualInvoiceManagerViewModel.kt`)
- **Sistem Penomoran Otomatis**:
  - Format standar: `INV/[TAHUN]/[PREFIX]/[SEQUENCE]` (contoh: `INV/2026/AJB/00142`).
- **Dual Mode Editor**:
  - Mode Direct Stock Sale (Penjualan barang jadi langsung dari katalog stok).
  - Mode Custom Project Sale (Penjualan produk kustom apparel dengan matriks ukuran & spesifikasi khusus).
- **Multi-Payment Engine**:
  - Mendukung pencatatan pembayaran bertahap (DP Awal, Cicilan Ke-N, Pelunasan).
  - Setiap pembayaran mencatat metode pembayaran (`TUNAI`, `TRANSFER`, `QRIS`, `DEBIT`), tanggal, dan jumlah nominal terbayar.
- **Pencetakan & Export**:
  - Fitur cetak dokumen faktur resmi dalam format PDF berstandar cetak industrial dan eksport data CSV.

### 5.6 Riwayat Transaksi & Audit Ledger (`RiwayatScreen.kt`, `ActivityLogScreen.kt`)
- **Konsolidasi Aliran Data Universal**:
  - Menggabungkan pergerakan stok, pembayaran invoice, pemasukan kas direct, dan pengeluaran operasional dalam satu garis waktu (*timeline*) yang teratur.
- **Filter Multi-Periode & Pencarian**:
  - Pencarian fleksibel berdasarkan nama customer, nomor invoice, kategori, dan rentang tanggal.
- **Proteksi Otorisasi Member**:
  - Otomatis menyaring transaksi sehingga pengguna dengan role MEMBER hanya melihat riwayat miliknya sendiri.

### 5.7 Pengaturan & Manajemen Sistem (`SettingsScreen.kt`, `MemberManagementModule.kt`, `FinanceConfigModule.kt`)
- **Pengaturan Profil Usaha**:
  - Nama Perusahaan, Alamat, Nomor WhatsApp CS, Logo Branding.
- **Parameter Keuangan**:
  - Nomor Prefix Invoice Default, PPN %, Rekening Bank Usaha.
- **Manajemen Anggota**:
  - Tambah/Edit Member, Reset Password, Pengaturan Hak Akses Role (Owner, Admin, Member), penetapan Tier Harga Member.
- **System Health & Backup**:
  - Backup data lokal, Sinkronisasi ulang cloud Firestore, dan Reset Database.

### 5.8 Kitab Digital / Digital Book (`SettingsDigitalBookModule.kt`)
- Pusat dokumentasi digital operasional perusahaan yang berisi Standar Operasional Prosedur (SOP), panduan kerja manufaktur, regulasi bisnis, serta materi referensi internal yang dapat diakses oleh Owner, Admin, dan Member.

---

## 6. SISTEM SINKRONISASI REAL-TIME & KETAHANAN DATA

### 6.1 Arsitektur Dual Persistence Engine (Offline-First)
- **Local Persistence**: Menggunakan **Room Database** (`AppDatabase.kt`, `RoomDao.kt`) sebagai penyimpanan utama *offline-first* agar aplikasi dapat beroperasi tanpa kendala dengan kecepatan respon 0ms saat koneksi internet terputus.
- **Cloud Real-time Sync**: Menggunakan **Firebase Firestore** via `FirebaseSyncManager.kt` & `EnterpriseSyncEngine.kt` untuk sinkronisasi multi-device & multi-user secara instan di background thread (`Dispatchers.IO`).

### 6.2 Cloud Listener Optimization & Cost Control
- Firestore Real-time Listener hanya diaktifkan saat layar/screen berstatus active (`Lifecycle.State.STARTED` / `STARTED`), mencegah quota read Firebase membengkak dan menghemat pemakaian baterai perangkat.
- Seluruh sinkronisasi data antar-layar menggunakan Single-Source-of-Truth Flow dari Room SQLite sehingga tidak terjadi duplikasi query cloud.

### 6.3 Deduplikasi & Rekonsiliasi State Otomatis
- Fungsi `deduplicateInvoicesInLocalDb()` secara berkala membersihkan duplikasi invoice berdasarkan nomor invoice unik.
- Perhitungan ulang `paidAmount` dan `status` invoice dilakukan secara otomatis dari agregasi daftar `InvoicePayment` unik.

---

## 7. REGULASI PENGEMBANGAN & ATURAN INTEGRITAS (DEVELOPER MANDATES)

Setiap pengembang atau AI Agent yang melakukan modifikasi pada basis kode aplikasi **WAJIB MEMATUHI PERATURAN BERIKUT**:

1. **STANDAR IDENTITAS RESMI**: Dilarang memasukkan alamat, email, atau kontak placeholder/fiktif. Selalu referensikan `BusinessIdentityProvider` (`Tangerang, Banten`, `yansart31@gmail.com`, `+62 877-7739-8813`, `Luxury Visual Identity & Custom Merch`).
2. **PATUH PALET WARNA RESMI**: Dilarang merusak atau mengubah warna dasar DNA aplikasi (`AgedGold`, `ShadowBlack`, `DeepTeal`, `AlertGreen`, `AlertRed`).
3. **SURFACE INTEGRITY**: Dilarang menambahkan efek background blur atau radial gradient buram yang merusak keterbacaan teks. Gunakan permukaan solid alpha dengan border tegas.
4. **ZERO CLIPPING BADGE**: Wajib menjaga presisi overlay badge agar selalu tampil utuh di luar batas ikon tanpa terpotong (`Box(modifier = Modifier.wrapContentSize())`).
5. **CLEAN LEDGER DETAIL PRESENTATION**: Pada tampilan detail Ledger & pembayaran Invoice, jangan sertakan keterangan "Operator", "Oleh: Owner/Lainnya", atau komponen "Jam/HH:mm" yang tidak valid; cukup tampilkan tanggal transaksi yang bersih (`dd MMMM yyyy`).
6. **THREAD ISOLATION & NULL SAFETY**: Operasi I/O berat (file export PDF, cetak Bluetooth thermal, query database) Wajib berjalan di `Dispatchers.IO`. Seluruh parsial string dan numerical casting wajib terlindungi null-safety (`orEmpty()`, `toDoubleOrNull()`).
7. **ISOLASI DATA MEMBER**: Pastikan seluruh query/filter data untuk pengguna bertipe MEMBER selalu menyaring secara tepat berdasarkan identitas personal pengguna (Nama, Email, WhatsApp).
8. **VERIFIKASI KOMPILASI MUTLAK**: Sebelum menyelesaikan setiap sesi pengerjaan, wajib menjalankan verifikasi kompilasi `compile_applet` dan memastikan build **100% SUCCESS** tanpa sekelibat kesalahan sintetis/kompilasi.

---
*YANSPROJECT.ID ENTERPRISE SYSTEM — High-Precision Engineering & Industrial Financial Intelligence.*
