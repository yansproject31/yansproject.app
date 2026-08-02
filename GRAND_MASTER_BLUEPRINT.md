# GRAND MASTER BLUEPRINT: YANSPROJECT.ID ENTERPRISE SYSTEM
> **DOKUMEN INDUK REGULASI, ARSITEKTUR, TEMA, & SPESIFIKASI PENGEMBANGAN APLIKASI**  
> *Sistem: YANSPROJECT.ID Enterprise Resource Planning (ERP) & Financial Intelligence System*  
> *Versi: 2.0 (Grand Master Release)*  
> *Status: AKTIF, MENJADI ACUAN MUTLAK & HUKUM TERTINGGI BASIS KODE*

---

## 1. PROLOG & IDENTITAS SISTEM

**YANSPROJECT.ID** adalah platform *Enterprise Resource Planning* (ERP) dan Manajemen Keuangan Cyber-Industrial tingkat tinggi yang dirancang khusus untuk mengelola:
1. **Manufaktur Custom Apparel & Konveksi** (Pakaian, Seragam, Printing Matrix Costing).
2. **Manajemen Proyek & Cetak Industrial** (Custom Project Workflow).
3. **Persediaan Stok Bahan & Produk Jadi** (Master Catalog, Variant & Inventory Ledger).
4. **Penagihan & Pembayaran Multi-Payment** (Dual Invoice Engine, DP, Cicilan, Pelunasan).
5. **Konsolidasi Arus Kas & Keuangan Global** (Inflow, Outflow, Sub-Ledger Kas, Profitability Analysis).
6. **Sistem Akses Terisolasi Multi-Role** (Owner, Admin, Member, Non-Member).

Seluruh pembaruan, penambahan fitur, pembenahan bug (bugfix), refactoring, dan modifikasi kode pada aplikasi ini **WAJIB MENGIKUTI DENGAN PATUH DAN KETAT** setiap spesifikasi, DNA warna, hierarki visual, arsitektur otorisasi, dan aturan pengembangan yang tertera di dalam dokumen Grand Master Blueprint ini.

---

## 2. DNA PALET WARNA & SISTEM TEMA (THEME COLOR PALETTE)

Sistem menggunakan tema **Cyber Emerald & High-Contrast Industrial Dark Canvas**. Warna tidak boleh dipilih secara acak; harus selalu mereferensikan konstanta warna resmi di `YansDesignSystem.kt` atau `Color.kt`.

### 2.1 Spesifikasi Warna Resmi (Color Palette)
| Nama Variable | Code Color (Hex) | Fungsi Utama & Hierarki Visual |
| :--- | :--- | :--- |
| `ShadowBlack` | `#071213` (`0xFF071213`) | Canvas latar belakang utama aplikasi (Ultra-dark cyan black). |
| `DeepCanvas` | `#0B1B1C` (`0xFF0B1B1C`) | Latar belakang modul/container tingkat 2 & surface section. |
| `DeepTeal` | `#163536` (`0xFF163536`) | Warna dasar tombol, card container, dan surface elemen UI. |
| `CyberEmerald` | `#0F433F` (`0xFF0F433F`) | Dark emerald accent untuk status active dan container terfokus. |
| `AgedGold` | `#C8A25D` (`0xFFC8A25D`) | **Aksen Kemewahan / Primary Brand**: Header teks, border premium, highlight angka utama. |
| `GoldMuted` | `#8C7240` (`0xFF8C7240`) | Sub-header, label deskripsi sekunder, dan garis pembatas halus. |
| `AlertGreen` | `#36D0A7` (`0xFF36D0A7`) | **Status Positif**: Pemasukan (Inflow), Lunas, Stok Aman, Online Status Indicator. |
| `AlertRed` | `#FF5252` (`0xFFFF5252`) | **Status Kritis**: Pengeluaran (Expense), Piutang Kritis, Badge Notifikasi, Action Hapus. |
| `StatusWarningGold` | `#FFC107` (`0xFFFFC107`) | **Status Peringatan**: DP/Piutang berjalan, Stok Tipis, Antrean Proyek. |
| `HighlightSoftCyan` | `#4FD1C5` (`0xFF4FD1C5`) | Aksen data sekunder, grafik, status pengerjaan, dan elemen interaktif. |
| `TextLight` | `#E2E8F0` (`0xFFE2E8F0`) | Teks utama pada container gelap agar keterbacaan (*readability*) 100% optimal. |
| `TextMuted` | `#94A3B8` (`0xFF94A3B8`) | Placeholder, caption kecil, dan informasi pendukung. |

### 2.2 Aturan Surface Integrity & Anti-Blur
- **Dilarang keras** menggunakan efek `radialGradient` kotor atau `blur` berat berlebihan pada latar belakang card/container yang menyebabkan efek "bayangan buram" (*blur artifacts*).
- Seluruh Card & Glassmorphic Container wajib menggunakan **Alpha Surface Solid** (seperti `Color(0x25163536)` atau `Color(0xFF0D1E1E)`) dengan garis tepi tegas (**Crisp Solid Border 0.8dp - 1.2dp**) dan corner radius yang konsisten (10.dp - 16.dp).

---

## 3. SISTEM PENGGUNA & OTORISASI (ROLE-BASED ACCESS CONTROL - RBAC)

Aplikasi memiliki 4 tingkatan hak akses pengguna yang mengatur secara tegas batasan data dan fitur yang dapat diakses:

### 3.1 Role OWNER (Pemilik & Super Admin)
- **Otorisasi**: Akses 100% penuh tanpa batas ke seluruh modul aplikasi.
- **Hak Khusus**:
  - Melihat seluruh ringkasan keuangan global (Kas, Profit, Margin, Modal Awal/Berjalan, Omset Total).
  - Melakukan approval/persetujuan transaksi & hapus data permanen.
  - Mengelola seluruh daftar anggota (*Member Management*) dan mengubah role pengguna.
  - Mengonfigurasi parameter keuangan (Prefix Invoice, Pajak PPN, Metode Pembayaran).
  - Mengakses Laporan Audit Health System & Reset Database.

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

### 3.4 Role NON-MEMBER / PUBLIC (Tamu)
- **Otorisasi**: Akses visual dasar sebelum otentikasi.
- **Fungsi**:
  - Membuka Katalog Produk Publik & Stok Bahan.
  - Mengajukan permintaan estimasi harga / order awal.
  - Halaman Login & Registrasi Akun Baru.

---

## 4. MODUL UTAMA & ARSITEKTUR FITUR ERP

### 4.1 Main Navigation Bar & Header (`MainScaffold.kt` & `BottomNavigationBar.kt`)
- **Top Header Bar**:
  - Logo Utama `YANSPROJECT.ID` dengan aksen `AgedGold`.
  - Status Indicator Pill: `ONLINE` (`AlertGreen`) atau `OFFLINE` (`AlertRed`).
  - Search Icon untuk pencarian cepat universal.
  - Bell Icon untuk Notifikasi Sistem dengan Overlaid Count Badge (Zero Clipping Offset: `x = 5.dp, y = (-5).dp`).
  - Quick Settings Button & Logout Action.
- **Bottom Navigation Bar (5 Tab Utama)**:
  1. `DASHBOARD`: Ikhtisar Keuangan, Cash Inflow, Outflow, Alert Bisnis, & Sub-Ledger Keuangan.
  2. `PROJECT`: Manajemen Pengerjaan Pesanan Custom, Matrix Costing Apparel, Progress Pengerjaan.
  3. `STOK`: Katalog Inventaris Produk & Bahan, Peringatan Stok Tipis, In/Out Stock Ledger.
  4. `INVOICE`: Pembuatan Faktur Penagihan (Direct & Custom), Tracking DP & Cicilan, Cetak/Export PDF.
  5. `RIWAYAT`: Auditing Transaksi Lengkap, Filter Multi-Periode, Download Laporan PDF & CSV.

### 4.2 Dashboard & Financial Intelligence Engine (`DashboardScreen.kt`)
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
  - Pengeluaran Operasional (*Expense Categories*).
  - Rekap Kas & Bank.
  - Hitungan Net Profit & Gross Profit.
  - Penjualan & Piutang Berjalan.

### 4.3 Custom Project & Apparel Matrix Pricing (`ProjectScreen.kt` & `CustomProjectViewModel.kt`)
- **Apparel Matrix Costing**:
  - Mendukung input matriks kombinasi ukuran (S, M, L, XL, XXL, 3XL, Custom) dan tipe lengan (Pendek, Panjang, Raglan, Custom).
  - Kalkulasi otomatis total kuantitas, harga per pcs, diskon nominal/persen, pajak PPN, dan Grand Total.
- **Tracking DP & Pelunasan**:
  - Pencatatan pembayaran awal (Uang Muka/DP) dengan verifikasi otomatis status `DP PRODUKSI` atau `LUNAS`.
- **Workflow Status Proyek**:
  - Flow berurutan: `Planning` -> `Production` -> `Ready` -> `Completed` -> `Delivered`.
  - Batching Produksi & Alokasi Material dari Stok.

### 4.4 Stok & Inventaris Management (`StockScreen.kt` & `StockManagerViewModel.kt`)
- **Master Catalog & Varian**:
  - Pengelompokan katalog produk, varian warna, dan ukuran.
- **Inventory Ledger & History**:
  - Tracking pergerakan barang (Stok Masuk, Stok Keluar, Retur, Penyesuaian Stock Opname).
- **Auto Stock Summary Trigger**:
  - Setiap perubahan pada varian stok secara otomatis memperbarui ringkasan total stok pada katalog induk.

### 4.5 Invoice & Dual Invoice Manager (`InvoiceScreen.kt`, `DualInvoiceEditorScreen.kt`, `DualInvoiceManagerViewModel.kt`)
- **Sistem Penomoran Otomatis**:
  - Format standar: `INV/[TAHUN]/[PREFIX]/[SEQUENCE]` (contoh: `INV/2026/AJB/00142`).
- **Dual Mode Editor**:
  - Mode Direct Stock Sale (Penjualan barang jadi dari katalog stok).
  - Mode Custom Project Sale (Penjualan produk kustom apparel dengan matriks ukuran & spesifikasi khusus).
- **Multi-Payment Engine**:
  - Mendukung pencatatan pembayaran bertahap (DP Awal, Cicilan Ke-N, Pelunasan).
  - Setiap pembayaran mencatat metode pembayaran (`TUNAI`, `TRANSFER`, `QRIS`, `DEBIT`), tanggal, serta admin pencatat.
- **Pencetakan & Export**:
  - Fitur cetak dokumen faktur resmi dalam format PDF berstandar cetak industrial dan eksport data CSV.

### 4.6 Riwayat Transaksi & Audit Ledger (`RiwayatScreen.kt`)
- **Konsolidasi Aliran Data Universal**:
  - Menggabungkan pergerakan stok, pembayaran invoice, pemasukan kas direct, dan pengeluaran operasional dalam satu garis waktu (*timeline*) yang teratur.
- **Proteksi Otorisasi Member**:
  - Otomatis menyaring transaksi sehingga member hanya melihat riwayat miliknya sendiri.

### 4.7 Pengaturan & Manajemen Sistem (`SettingsScreen.kt`, `MemberManagementModule.kt`, `FinanceConfigModule.kt`)
- **Pengaturan Profil Usaha**:
  - Nama Perusahaan, Alamat, Nomor WhatsApp CS, Logo Branding.
- **Parameter Keuangan**:
  - Nomor Prefix Invoice Default, PPN %, Rekening Bank Usaha.
- **Manajemen Anggota**:
  - Tambah/Edit Member, Reset Password, Pengaturan Hak Akses Role (Owner, Admin, Member).
- **System Health & Backup**:
  - Backup data lokal, Sinkronisasi ulang cloud Firestore, dan Reset Database.

### 4.8 Kitab Digital / Digital Book (`SettingsDigitalBookModule.kt`)
- Pusat dokumentasi digital operasional perusahaan yang berisi Standar Operasional Prosedur (SOP), panduan kerja manufaktur, regulasi bisnis, serta materi referensi internal yang dapat diakses oleh Owner, Admin, dan Member.

---

## 5. SISTEM SINKRONISASI REAL-TIME & KETAHANAN DATA

### 5.1 Arsitektur Dual Persistence Engine
- **Local Persistence**: Menggunakan **Room Database** (`AppDatabase.kt`) sebagai penyimpanan utama offline-first agar aplikasi dapat beroperasi tanpa kendala saat koneksi internet terputus.
- **Cloud Real-time Sync**: Menggunakan **Firebase Firestore** via `FirebaseSyncManager.kt` & `EnterpriseSyncEngine.kt` untuk sinkronisasi multi-device & multi-user secara instan.

### 5.2 Deduplikasi & Rekonsilasi Otomatis
- Fungsi `deduplicateInvoicesInLocalDb()` secara berkala membersihkan duplikasi invoice berdasarkan nomor invoice unik.
- Perhitungan ulang `paidAmount` dan `status` invoice dilakukan secara otomatis dari agregasi daftar `InvoicePayment` unik.

---

## 6. REGULASI PENGEMBANGAN & ATURAN INTEGRITAS (DEVELOPER MANDATES)

Setiap pengembang atau AI Agent yang melakukan modifikasi pada basis kode aplikasi **WAJIB MEMATUHI PERATURAN BERIKUT**:

1. **JANGAN PERNAH** mengubah atau merusak warna dasar DNA aplikasi (`AgedGold`, `ShadowBlack`, `DeepTeal`, `AlertGreen`, `AlertRed`).
2. **JANGAN PERNAH** menambahkan efek background blur atau radial gradient buram yang merusak keterbacaan teks.
3. **WAJIB MENJAGA** presisi overlay badge agar selalu tampil utuh tanpa terpotong (*zero clipping rule*).
4. **SETIAP MODUL BARU** wajib menggunakan Jetpack Compose dengan Material Design 3 yang disesuaikan dengan `YansDesignSystem.kt`.
5. **ISOLASI OTORISASI**: Pastikan seluruh query/filter data untuk role MEMBER selalu menyaring berdasarkan identitas personal pengguna.
6. **VERIFIKASI KOMPILASI**: Sebelum menyelesaikan tugas, wajib menjalankan verifikasi kompilasi `compile_applet` dan memastikan build **100% SUCCESS**.

---
*YANSPROJECT.ID ENTERPRISE SYSTEM — High-Precision Engineering & Industrial Financial Intelligence.*
