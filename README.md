# onvo-tv-tools

1 GB RAM'li bir Android TV'yi (Onvo 43VQ80F2FA, WhaleOS 10 / Android 13) kullanılabilir hale
getirirken yazılanlar. 14 Eylül 2026.

Ana parça **TOD Turbo**: tek dokunuşla arka plandaki süreçleri kırpıp TOD uygulamasını
`FLAG_ACTIVITY_CLEAR_TASK` ile sıfırdan açan 83 satırlık bir launcher. React Native + Hermes
uygulamalarında donmanın asıl sebebi genelde donmuş eski oturumdur; Turbo onu kapatır.

Ölçüm (YouTube arka planda, sonra TOD açılışı): direkt açılışta 92 MB, Turbo ile 78 MB.
Bellek farkı küçük, asıl kazanç temiz başlatma.

Yanında **başarısız bir deneme** de bilerek duruyor (`denenen-ve-olmayan/disney-shim`):
kumandadaki Disney tuşuna TOD bağlama girişimi. Olmadı, sebebi aşağıda.

APK: [Releases](../../releases) sayfasında. Kaynaktan derlemek için `tod-turbo/build.ps1`.


## Klasörler

```
tod-turbo/                  ← çalışan uygulama (TV'de kurulu)
  AndroidManifest.xml       ← uygulamanın kimlik kartı
  src/.../TurboActivity.java ← tek kod dosyası, 83 satır
  res/drawable-xhdpi/       ← banner + simge (Python/PIL ile üretildi)
  build.ps1                 ← derleme betiği, adım adım yorumlu
  (APK depoda değil, Releases sayfasında)

denenen-ve-olmayan/
  disney-shim/              ← ÇALIŞMADI, bilerek duruyor
```

## Okuma sırası

**1. `tod-turbo/AndroidManifest.xml`** — kod değil, bildirim. Android'e "ben kimim, ne izin istiyorum,
beni nasıl açarsın" der. Bakılacak 3 satır:

- `KILL_BACKGROUND_PROCESSES` → istediğimiz izin. Normal seviye, kullanıcıya sormadan verilir.
- `LEANBACK_LAUNCHER` → "beni TV ana ekranında göster". Telefon uygulamalarında `LAUNCHER` yeter, TV'de bu da lazım.
- `<queries>` → Android 11+ bir uygulama diğerlerini göremez. TOD'u görebilmek için adını burada yazmak zorundayız.

**2. `tod-turbo/src/.../TurboActivity.java`** — asıl iş. Aşağıda satır satır.

**3. `tod-turbo/build.ps1`** — Android Studio açmadan APK üretmenin 7 adımı.

**4. `denenen-ve-olmayan/disney-shim/`** — kumandadaki Disney tuşuna TOD bağlama denemesi.
Fikir: Disney+ silinmişti, onun paket adıyla (`com.disney.disneyplus`) sahte bir uygulama kurarsak
tuş onu açar, o da TOD'u açar. **Olmadı:** Disney+ sistem uygulaması, paket adı üreticinin imzasına
kilitli. `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Başarısız denemeler de bilgidir, o yüzden duruyor.

## TurboActivity.java satır satır

```java
public class TurboActivity extends Activity
```
`Activity` = bir ekran. Ama bu ekranın görüntüsü yok (manifest'te `Theme.Translucent.NoTitleBar`).
İşini yapıp kendini kapatıyor. Kullanıcı sadece TOD'un açıldığını görüyor.

```java
private static final String[] KILL = { ... }
```
Kapatılacak paketlerin listesi. `static final` = sabit, çalışma sırasında değişmez.
Listede TOD'un kendisi de var: donmuş eski oturumu kapatıp sıfırdan başlatmak için.

```java
protected void onCreate(Bundle savedInstanceState)
```
Uygulama açılınca Android'in çağırdığı ilk metot. Programın giriş kapısı.

```java
am.killBackgroundProcesses(pkg);
```
Asıl komut. **Sınırı burada:** Android 13'te bu çağrı başka uygulamalara izin verir ama
sistem "arka planda" saymadığı bir süreci kapatmaz. Ölçtük: 19 çağrının hepsi hatasız döndü,
hiçbiri tam kapatmadı, sadece kırptı. (Android 14'te bu API tamamen kendi uygulamanla sınırlandı.)

```java
} catch (RuntimeException e) {
    Log.w(TAG, "kill fail " + pkg + ": " + e);
}
```
İlk versiyonda buranın içi boştu (`catch (RuntimeException ignored) {}`) ve **hata yiyordu**.
Neden kapanmadığını göremedim. Ders: hatayı sessizce yutma, en azından logla.
`adb logcat | grep TodTurbo` ile okunuyor.

```java
new Handler(Looper.getMainLooper()).postDelayed(new Runnable() { ... }, 700);
```
"700 milisaniye sonra şunu çalıştır." Neden bekliyoruz: kapatma isteği anında bitmiyor,
sistemin belleği geri toplaması zaman alıyor. Hemen TOD'u açarsak boşalmamış belleğe açmış oluruz.
`Looper.getMainLooper()` = ana iş parçacığı; arayüze dokunan her şey orada çalışmak zorunda.

```java
launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
```
**Dosyadaki en önemli satır.** `CLEAR_TASK` = TOD'un eski oturumunu çöpe at, sıfırdan başlat.
Bu olmasaydı donmuş TOD'a geri dönerdik. Ölçümde TOD'un 62 MB'ı swap'a sıkışmıştı ve
oradan geri gelmiyordu; tek çare yeniden başlatmak.

```java
finish();
```
Kendini kapat. Turbo'nun RAM'de kalmaması lazım, yoksa çözmeye çalıştığı soruna katkıda bulunur.

```java
private static long availMb(ActivityManager am)
```
Boş bellek okur. Sadece "+X MB boşaltıldı" yazısı için. İşin özüne etkisi yok.

## Dürüst değerlendirme

Bu uygulamanın ölçülen RAM kazancı **küçük** (~15 MB). Asıl faydası `CLEAR_TASK`:
TOD'u her seferinde temiz başlatması. Donmanın gerçek sebebi buydu.

Gerçek kapatma için tek yol, uygulamanın TV'nin kendi ADB'sine (localhost:5555) bağlanıp
`am kill` çalıştırması. Yapılabilir ama ADB protokolü + RSA kimlik doğrulama gerekir, ve
5555'in sürekli açık kalması lazım. Kazanç/karmaşıklık oranı tutmadığı için yapılmadı.
