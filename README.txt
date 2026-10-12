CAMPUS COMPANION - STEPS 1-3 STARTER
====================================
1. Android Studio Hedgehog -> New Project -> Empty Views Activity
   Language: Java | Min SDK 24 | Package: com.<yourname>.campuscompanion
   (Do NOT accept any "Upgrade AGP" popup.)
2. Copy app/src/main/java/... and app/src/main/res/... from this zip into your project.
   If your package is not com.yourname.campuscompanion:
   Ctrl+Shift+R (Replace in Files): com.yourname  ->  com.<yourname>
   Also delete the wizard's own MainActivity and activity_main.xml first (this zip replaces them).
3. Apply build-gradle-additions.txt to app/build.gradle, then manifest-additions.xml
   to AndroidManifest.xml. Sync Gradle.
4. Run on your phone. Add a deadline, tick it, long-press to delete.

GIT / GITHUB (do this right after step 1, before copying code)
--------------------------------------------------------------
1. github.com -> New repository -> name: campus-companion -> do NOT add README/.gitignore.
2. In Android Studio's Terminal (project root):
   git init
   git add .
   git commit -m "Initial commit: empty Android project"
   git branch -M main
   git remote add origin https://github.com/<username>/campus-companion.git
   git push -u origin main
   (First push opens a browser login. If asked for a password, use a Personal Access Token.)
3. Commit + push after every milestone:
   git add .
   git commit -m "Add Room entities and DAOs"
   git push
