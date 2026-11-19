# פישל – אפליקציית צ'אט קטנה

האפליקציה כוללת קבוצת פישל אחת שפתוחה לכל משתמש שנכנס, ויצירת שיחות פרטיות לשני משתתפים באמצעות קוד חד-פעמי.

## מה כבר נמצא בפרויקט
- ממשק עברי RTL בסגנון אפליקציית הודעות.
- קבוצת פישל משותפת.
- יצירת שיחה פרטית וקוד הזמנה.
- הצטרפות לשיחה עם קוד; עד שני משתתפים.
- Firebase Authentication אנונימי ו-Firestore בזמן אמת, לאחר הגדרת פרויקט Firebase.
- מצב הדגמה מקומי ללא Firebase (הודעות במכשיר בלבד).

## הפעלה מקוונת – נדרש חיבור Firebase חד-פעמי
1. פתח פרויקט ב-Firebase Console.
2. הפעל Authentication > Sign-in method > Anonymous.
3. צור Cloud Firestore.
4. העתק את פרטי Web App אל firebase-config.js.
5. פרסם את firestore.rules במסך Firestore Rules.

בלי Firebase מוגדר, האפליקציה אינה שולחת הודעות בין מכשירים. GitHub הוא אחסון הקוד ולא שרת הצ'אט.

## בניית APK
קובצי Android נוצרים בתהליך GitHub Actions. פתח Actions בפרויקט, בחר workflow בשם Build Fishel Android APK, ולחץ Run workflow. בסיום הורד את artifact בשם fishel-debug-apk.
