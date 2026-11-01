package com.example.campuscompanion.ui;

import android.content.pm.PackageInfo;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.campuscompanion.databinding.ActivityAboutBinding;

/** Short "About" page: what the app is, the course topics it uses, and the privacy note. */
public class AboutActivity extends AppCompatActivity {

    private static final String TECH =
            "• Room (SQLite): attendance, notes, topics, routine, deadlines, with migrations\n"
            + "• Retrofit + Gson: live weather over HTTP and JSON\n"
            + "• ContentProvider: deadlines readable through a content:// URI\n"
            + "• WorkManager: background sync, daily revision reminder, optional backup\n"
            + "• AlarmManager + notifications: deadline, study and attendance alerts\n"
            + "• Foreground service: the next-deadline tracker\n"
            + "• App widgets: next deadline, and today's classes and revision\n"
            + "• Firebase Auth + Firestore: Google, Facebook and email sign-in, class notices, backup\n"
            + "• Material 3: light and dark themes, bottom navigation, bottom sheets\n"
            + "• PdfDocument + FileProvider: shareable attendance report";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityAboutBinding b = ActivityAboutBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        String version = "";
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            version = "Version " + info.versionName;
        } catch (Exception ignored) { }
        b.tvVersion.setText(version);
        b.tvTech.setText(TECH);
    }
}
