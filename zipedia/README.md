# זימפדיה — קורא ZIM מקומי

ממשק קריאה בעברית בהשראת ויקיפדיה עבור ארכיוני **ZIM** המשמשים את Kiwix.

## מה יש כאן

- ייבוא קובץ ‎`.zim`‎ מהמחשב, וגם בחירת חלקים של ארכיון מפוצל כאשר כולם שייכים לאותו ארכיון.
- תוכן עניינים לפי אותיות בעברית ובאנגלית, וחיפוש לפי כותרת.
- חיפוש מלא בטקסט של ארכיונים שכוללים אינדקס מתאים.
- פתיחת ערכים, קישורים פנימיים ותמונות מתוך הארכיון.
- ממשק רספונסיבי למחשב ולנייד.

הקובץ נפתח מקומית בדפדפן; תוכן ה-ZIM אינו נשלח לשרת. כדי לטעון את האפליקציה והקורא בפעם הראשונה נדרש חיבור לאינטרנט. איכות החיפוש המלא והזמינות של חלק מהמדיה תלויות בארכיון עצמו.

## פיתוח ופריסה

ה-Workflow שב-`.github/workflows/deploy-zipedia.yml` מוריד במהלך הפריסה את קובצי WebAssembly של [javascript-libzim](https://github.com/openzim/javascript-libzim), מוסיף להם פעולת דפדוף עם דפדוף בין דפים, בודק את תחביר JavaScript ומפרסם את התיקייה הזו ב-GitHub Pages.

הקורא מבוסס על פרויקט open-source של OpenZIM. קובצי הליבה יורדים בעת build ואינם נשמרים כקבצים בינאריים במאגר.

## שימוש

1. פתח את אתר GitHub Pages של הפרויקט.
2. לחץ על **ייבוא קובץ ZIM** או גרור קובץ לתיבה.
3. בחר אות כדי לעיין בתוכן העניינים, או השתמש בחיפוש בכותרות.
4. בחר ערך מהרשימה כדי לקרוא אותו.

הערה: ארכיוני ZIM גדולים יכולים לדרוש זמן וזיכרון משמעותיים בדפדפן. חיפוש בתוך הטקסט זמין רק בקבצים שכוללים אינדקס חיפוש מלא.


## Open in your browser

After the GitHub Pages deployment succeeds, open: https://d0548448174-ai.github.io/app/

## Open-source reader

The WebAssembly reader is built from the tagged OpenZIM source in `v0.95`. See [OPEN_SOURCE_NOTICES.md](OPEN_SOURCE_NOTICES.md) for the upstream license and the small build-time patches used for the alphabetical title index.
