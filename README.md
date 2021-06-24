# SMS Backup+ <img src="https://raw.githubusercontent.com/jberkel/sms-backup-plus/master/metadata/play/assets/sms-backup.svg?sanitize=true" height="50px" alt="SMS Backup+ logo"/> [![Build Status SVG][]][Build Status] [![Open Source Helpers](https://www.codetriage.com/jberkel/sms-backup-plus/badges/users.svg)](https://www.codetriage.com/jberkel/sms-backup-plus)

[<img alt="Get it on Google Play" src="https://jberkel.github.io/sms-backup-plus/assets/img/google-play-badge.png" height="80pt"/>][Google Play Store] [<img alt="Get it on F-Droid" src="https://jberkel.github.io/sms-backup-plus/assets/img/f-droid-badge.svg" height="80pt"/>][F-Droid]

---------------------
## Reporting bugs

Please read the [known issues](BUGS.md) before reporting any new issues; we already know about several significant issues, including Gmail login failures (for which there is a robust work-around), and problems with logging phone calls into calendars (which we are investigating).

---------------------
## Description

This is a fork of the now-defunct Android backup tool
[SMS Backup](http://code.google.com/p/android-sms). It uses Gmail/IMAP to perform SMS, MMS and call log backups over
the network.

Main features/improvements:

  * Restore. SMS/Call logs stored on Gmail can be transferred back to the phone.
    * ⚠️ MMS are not restored.

  * Security. SMS Backup+ does not need your Gmail password.
    * Where possible, SMS Backup+ uses [XOAuth2], including when matching phone numbers against
      contact names, and writing call log records into your calendar. This access can be revoked at any time.
    * ⚠️ In June 2019 Google [changed their API policy], introducing [sensitivity scopes], and
      as a result SMS Backup+ [can no longer use XOAuth2] to save messages into your mailbox;
      instead a "password" is required to access Gmail via IMAP, but it can be a generated
      [application password][Sign in using App Passwords]
      (which can be revoked without affecting your real password).

  * MMS backup support (added in version 1.1)

  * Call log backup (version 1.2), with Google Calendar integration
    (1.3) and restore (1.4).

  * Works with any IMAP server (but defaults to Gmail).

Tested with Android 4.x (Ice Cream Sandwich) - 10.x. (Q)

SMS Backup+ is available for free in the [Google Play Store] and on [F-Droid],
there will never be a pro / paid version.

But if you find the app useful and want to support its development you can make a donation
using the secure in-app Play Store payment mechanism.

To get updates more frequently join the [beta programme](#beta) or download the latest beta manually
from [Github releases].

Also make sure to read the [Privacy Policy][].

## Usage

### First steps

You need to have an IMAP account or a Gmail account with IMAP enabled. See
[Use IMAP to check Gmail on other email clients][] to learn how to enable IMAP for your Gmail
account or look at this [screenshot][imapenableshot].

After starting SMS Backup+, tap on "Connect" to start the authorization process.

👉 1.5.11: You will first have give SMS Backup+ the permission to access your account, displayed
as "Allow SMS Backup+ to access your contacts?".

If you consent you have to confirm the Gmail account to be used for the backup.

If you don't have a Google account registered on your device a browser window
will appear to perform a web-based authentication as fallback.

After completing the authorization process the "Connect"
switch should be activated, indicating success. You are now ready to perform
the first backup.

👉 1.5.11: "Connected" changed from checkbox to switch.

### Initial backup

It is important that you perform the first backup manually. SMS Backup+ needs
to know whether you want to upload messages currently stored on your device or
not.

After having connected your Gmail account, SMS Backup+ will ask you to perform
a first backup. If you choose "Backup", SMS Backup+ will start backing up all
your messages to Gmail.

If you choose "Skip", nothing is sent to Gmail and all messages currently
stored on your device are simply marked "backed up". This option is handy if
you previously uninstalled SMS Backup+ and do not want to send your messages
again to Gmail. Please note that any messages arrived after you last
uninstalled SMS Backup and this initial backup won't ever be backed up to
Gmail.

👉 1.5.11: Before the backup starts you will have to grant "send and
view" permissions for SMS.

### Restoring

If you wish to restore messages back to your phone tap "Restore". By default
all messages stored on Gmail will be restored (this can be changed in "Advanced
Settings").

👉 1.5.11: Default changed to 500 messages.

You can safely restore to a phone which has already messages stored
on it, SMS Backup+ will skip the existing messages.

Before the restore can start you will need to confirm the change of the
default SMS app. This step is required to get further permissions to write
messages (see also the corresponding [FAQ entry](#faq-restore-default-app)).

### Call log support

SMS Backup+ can also backup and restore your call logs. It stores all calls using a
separate label (defaults to `Call log`, but can be changed in "Advanced
settings"). If you wish you can set this to the same value as `SMS`, to make all
backups use the same label.

The body of the call log message contains the duration of the
call in seconds followed by the phone number and call type (`incoming` /
`outgoing` / `missed`).

An example:

    267s (00:04:07)
    +44123456789 (incoming call)

You can also add call log entries to a Google calendar. Just select `Calendar
sync` in Advanced settings, and make sure you have selected a calendar to sync
with.

If you only want to backup specific call types (incoming, outgoint etc.) you can
do so as well.

👉 1.5.11: Call log backup is disabled by default. Permissions need to be confirmed
separately for phone and calendar access.

### <a id="RCS-support">Rich Communication Services</a>

[Rich Communication Services](https://en.wikipedia.org/wiki/Rich_Communication_Services) is currently not supported, nor are there any plans to support it.
Rich Communication Services is also known as _RCS_, _Advanced Messaging_, _Advanced Communications_, _joyn_ or _Message+_.

### <a id="3rdparty">3rd party app integration</a>

If you want to trigger backups from another app, enable `3rd party integration`
in Advanced Settings and send the broadcast intent
`com.zegoggles.smssync.BACKUP`. This will work even when Auto Backup is
disabled.

### Usage without Gmail (IMAP)

You don't have to use Gmail to backup your text messages - change
Authentication to "Plain text" in "Advanced settings - Custom IMAP server",
then change the server address / user name and password accordingly. Make sure
to set security to "SSL (optional / trust all)" if your IMAP server
has a self-signed certificate ("Unknown certificate" error during
backup).

👉 1.5.11: security settings have been simplified. Select TLS and check
"Trust all certificates" if using a self-signed certificate.

Also note that Gmail labels simply correspond to IMAP folders that will
automatically get created on the first backup.

## <a id="faq">FAQ</a>

If you don't find an answer here you can also visit the [Google+ community][] which might be able
to help.

  * [General questions](#faq-general)
    * [I want to file a bug report, what should I do?](#faq-general-file-bug-report)
    * [Can you add feature X?](#faq-general-can-you-add-feature-x)
    * [Why does it need so many permissions?](#faq-general-permissions)
    * [When updating the app I get 'Package file was not signed correctly'](#faq-general-package-not-signed)
  * [Backup questions](#faq-backup)
    * [Automatic backup does not work / stopped working](#faq-backup-automatic-backup)
    * [I get the one of following errors during backup/restore: Command: SELECT "SMS"; response:...](#faq-backup-show-imap)
    * [Only received messages are backed up, not the ones I sent](#faq-backup-only-received)
    * [How can I make the app think that it has to do the backup again?](#faq-backup-reset)
    * [Why do backed up SMS show up in my inbox?](#faq-backup-inbox)
    * [I want the backed up messages to show up in my Gmail inbox!](#faq-backup-to-inbox)
    * [What's the difference between regular and incoming backup schedule?](#faq-backup-schedule)
    * [I'd like SMS Backup+ to schedule a backup only at a given time of the day / when Wifi is available / etc.](#faq-backup-scheduling)
    * [The app saves only 100 SMS/MMS per contact!](#faq-backup-gmail-100)
    * [In Gmail, I'd like to have all messages listed chronologically and not ordered by who sent them.](#faq-backup-threading)
    * [When I delete a text locally, will it delete the saved copy on Gmail?](#faq-backup-does-it-sync)
    * [I get the error "Trust anchor for certification path not found"](#faq-backup-untrusted-certificate)
  * [Restore questions](#faq-restore)
    * [Why does SMS Backup+ ask to become the default SMS app?](#faq-restore-default-app)
    * [Are there any plans to support restoring of MMS?](#faq-restore-MMS)
    * [I'm not able to restore all of my (insert huge number) messages!](#faq-restore-many-messages)
    * [How do I restore the last N weeks / N messages?](#faq-restore-partial)
    * [The timestamps of the restored messages is wrong / the messages are not restored in the right order](#faq-restore-reversed)
  * [Authentication questions](#faq-authentication)
    * [How can I revoke the app's access to my Gmail account?](#faq-authentication-revoke-access)
    * [When connecting, I get 'Could not obtain request token...'](#faq-authentication-request-token)

### <a id="faq-general">General questions</a>

#### <a id="faq-general-file-bug-report">I want to file a bug report, what should I do?</a>

First search [Github issues][] to see if the bug has already been reported. If not, create a new
issue and attach the following details:

 * Version of SMS Backup+ used
 * Version of Android / brand of phone used

If it is related to backing up / restoring you should also enable the sync log with
"Extra debug information" enabled (in "Advanced settings") and attach a relevant portion of it.
The sync log is stored as `sms_backup_plus.log` (in the directory `Android/data/com.zegoggles.smssync/files`).

👉 1.5.11: new log file path, was previously on external storage / sdcard.

Rather than including the log in the text of your issue description, please use the "attach
image" feature to attach your logfile _as text_ to your issue.
Alternatively, create a [gist](https://gist.github.com) and link to it from your issue description.

It might also be worth to install the [current beta version](#beta) of SMS Backup+ to
see if the bug is also present in the development version.

#### <a id="faq-general-can-you-add-feature-x">Can you add feature X?</a>

Over the years a lot of features have been added, often as a result of
requests by users. This has worked great initially but has made the product itself very unfocussed
and generic. It started as a tool to back up text messages (as the name *SMS* Backup implies) but
gradually more and more features were added (call logs, MMS, WhatsApp...). It's now at a point where
it has become too heavy and difficult too maintain or use. The settings screen makes this obvious,
there are just too many things to configure. If anything features should be removed at this point,
not added. A more focussed product would be easier to maintain and use.

Right now, SMS Backup+ is in maintenance mode; no new features will be added. Existing bugs will of
course be addressed.

#### <a id="faq-general-permissions">Why does it need so many permissions?</a>

  * Read contacts - Needed to map phone numbers to names and email addresses
  * Your messages (read / write SMS) - Needed for backup+restore
  * Modify calendar events - needed for the call log backup to GCal
  * Send email to guests - this refers to calendar invitations (which are not created by the app)
  * Prevent phone from sleeping - needed to keep network active during a backup
  * Find accounts on the device - used for authentication
  * Use accounts on the device - used for authentication
  * Google Play billing service - used for in-app donations
  * Run at startup - used to enable automatic backups after reboot

👉 1.5.11 introduces runtime permissions (Android 6.0+) which means that you only grant the permissions
for the features you actually use, after installing the application.

#### <a id="faq-general-package-not-signed">When updating the app I get 'Package file was not signed correctly'</a>

Try uninstalling the app, then installing the new version. Make sure to select
"Skip" when doing the first backup, otherwise messages will get backed up
twice.

### <a id="faq-backup">Backup questions</a>

#### <a id="faq-backup-automatic-backup">Automatic backup does not work / stopped working</a>

If the automatic backup does not work first make sure that a manually
initiated backup works as expected.

👉 1.5.11 brings many improvements to auto-backup reliability. However some beta users have
still reported problems. If the backups don't run automatically try changing the Android
"Battery optimization" settings for SMS Backup+ to "Don't optimize".

When reporting a bug related to auto backup it is essential to attach a sync log file. See
the [relevant FAQ](#faq-general-file-bug-report) for more information on how to do this.

#### <a id="faq-backup-show-imap">I get the one of following errors during backup/restore: Command: SELECT "SMS"; response: \#6\# \[NO, Unknown, Mailbox; SMS, \[Failure\]\] (or response: \#6\# \[NO, \[NONEXISTENT\], unknown mailbox: SMS (failure)\])</a>

Make sure you have the "Show IMAP" option checked in the Gmail label settings:

![Screenshot][showimap]

If this is the case make sure that the label name is set correctly (capitalization
matters!).

#### <a id="faq-backup-reset">How can I make the app think that it has to do the backup again?</a>

Select "Reset" from the menu, and confirm that you want to reset the current
sync state. All messages on the phone will be backed up on the next run.

#### <a id="faq-backup-only-received">Only received messages are backed up, not the ones I sent</a>

Do you use a non-standard app like Google Voice/Hangouts or Signal to send
messages? It could also be a device specific problem.
Related issues: [516][], [841][].

#### <a id="faq-backup-inbox">Why do backed up SMS show up in my inbox?</a>

This is probably related to Gmail's automatic priority inbox filing.
A workaround is to set up a filter with "subject: SMS with", let the filter
mark it as not important.

![](https://raw.github.com/jberkel/sms-backup-plus/gh-pages/screenshots/20120106-tymk3rn4i5apshhr6e1hbd17qn.jpg)
![](https://raw.github.com/jberkel/sms-backup-plus/gh-pages/screenshots/20120106-rsg7912rnus5gwe3e572rxwbae.jpg)

#### <a id="faq-backup-to-inbox">I want the backed up messages to show up in my Gmail inbox!</a>

Just set the label to "Inbox" instead of the default "SMS" or "Call log".

#### <a id="faq-backup-schedule">What's the difference between regular and incoming backup schedule?</a>

Incoming backup schedule is used for incoming messages. 3 minutes here means
that any incoming SMS will trigger a backup after 3 minutes. It is a full
backup (including any sent messages). You should set the incoming schedule to a
low value if you want to make sure that incoming SMS show up in
Gmail shortly after arrival.

Regular schedule is used to perform backups in specific intervals. 2 hours here
means that the device will try to backup all messages every 2 hours.

Fewer updates performed by the app means less energy consumed, so there's
a trade-off data protection vs. battery life.

#### <a id="faq-backup-scheduling">I'd like SMS Backup+ to schedule a backup only at a given time of the day / when Wifi is available / etc.</a>

If you require more control over the backup schedule than what SMS Backup+ already
provides you can use a 3rd party app to trigger the backup. [Tasker][] for
example supports SMS Backup+ since version 1.0.14.

#### <a id="faq-backup-gmail-100">The app saves only 100 SMS/MMS per contact!</a>

This seems to be a limitation of Gmail. After the first hundred or so SMS being
backed up, Gmail will cease to properly thread many of the conversations.
You will notice that Gmail will eventually treat each SMS (in that initial
backup) as individual conversations and will not longer group/thread them
together.

A way around this is to do a full backup 100 SMS at a time (see `Advanced
settings`).

#### <a id="faq-backup-threading">In Gmail, I'd like to have all messages listed chronologically and not ordered by who sent them.</a>

It's a Gmail feature, but you can disable it.
In Gmail settings, set conversation view to `off`
([screenshot][converationviewoff]).

#### <a id="faq-backup-does-it-sync">When I delete a text locally, will it delete the saved copy on Gmail?</a>

No. SMS Backup+ does not do a "real" sync, once the text has been transferred
to Gmail it won't get modified or deleted by the app.

#### <a id="faq-backup-untrusted-certificate">I get the error "Trust anchor for certification path not found"</a>

This means that it is impossible to establish a trusted connection with the IMAP server. A few
reasons why this might happen:

  * You run on an ancient version of Android
  * You use an IMAP server with an expired or self-signed certificate

You can try to set the IMAP Server Setting `Security` to `SSL (optional / trust all)`.

👉 1.5.11: Added "Trust all certificates" option

### <a id="faq-restore">Restore questions</a>

#### <a id="faq-restore-default-app">Why does SMS Backup+ ask to become the default SMS app?</a>

Google has changed the way SMS permissions work starting with Android 4.4
(KitKat). In order to get write access to your messages an app has to be set as
the default system SMS app. Before starting the restore operation SMS Backup+
will request your permission to be set as default app. After restoring you will
be asked to set the previous choice (usually "Messaging") back as default.
⚠️ This last step is important, if you don't set the old default back you might
lose new messages.

#### <a id="faq-restore-MMS">Are there any plans to support restoring of MMS?</a>

No, for a variety of reasons: MMS are highly carrier-dependent, documentation is lacking and the
Android Emulator does not support them.

However SMS Backup+ is open source; patches are more than welcome.

#### <a id="faq-restore-many-messages">I'm not able to restore all of my (insert huge number) messages!</a>

SMS Backup has not been designed to restore many thousands of messages. See the
question "[How do I restore the last N weeks / N messages?](#faq-restore-partial)" for a way around that.

#### <a id="faq-restore-partial">How do I restore the last N weeks / N messages?</a>

If you have a lot of messages backed up (let's say over 5000) restoring can be
very slow, especially if you're only interested in the most recent messages.

A workaround is to use the Gmail web interface (or an IMAP email client) to
move the bulk of the messages to another label in Gmail (e.g. SMSARCHIVED), and
only keep a few hundred or so messages in the SMS label.

Next time you restore it will only restore those messages and it will be a lot
faster.

### <a id="faq-authentication">Authentication questions</a>

#### <a id="faq-authentication-revoke-access">How can I revoke the app's access to my Gmail account?</a>

Go to [Authorized Access to your Google Account][] and select "Remove Access"
next to "SMS Backup+".

#### <a id="faq-authentication-request-token">When connecting, I get 'Could not obtain request token...'</a>

If you get this error message and your network connection is active
double-check that your time zone settings are correct, and that the local time is
displaying correctly. The authentication process won't work otherwise.

## <a id="beta">Beta testing</a>

If you want to help beta testing, visit the [Play Store beta page]. You will be asked to opt in
to the beta program (you can leave it anytime).

Once opted in your device will automatically update to the latest beta which might have bug fixes
and features not found in the currently released version.

Alternatively you can download an APK from [Github releases][] and install it manually
(⚠️ you won't get automatic updates this way). You can also install via [F-Droid][]
which often has more recent versions than what is available on the Play Store.

## <a id="contributing">Contributing</a>

### Installation from source

    $ git clone https://github.com/jberkel/sms-backup-plus.git
    $ cd sms-backup-plus
    $ ./gradlew assembleDebug
    $ adb install app/build/outputs/apk/app-debug.apk

### <a id="translating">Translating the UI</a>

If you want to help translating the UI to other languages download and
translate the following file, then send the translated version via email:

  * [strings.xml][]

However, if you're already familiar with Git you can just clone the
repository and submit a [pull request][About pull requests].

## <a id="credits">Credits</a>

  * [Christoph Studer](http://studer.tv/) Original author of SMS Backup (2009-2010)
  * [Ben Dodson](https://github.com/bjdodson) - Contacts 2.0 / MMS support
  * [Felix Knecht](https://github.com/dicer) - Call log backup code
  * [Michael Scharfstein](https://github.com/smike) - Call log calendar ICS support
  * [K-9 Mail](https://github.com/k9mail/k-9/) IMAP library, with some modifications ([k-9/sms-backup-plus](https://github.com/jberkel/k-9/tree/sms-backup-plus))
  * [signpost](https://github.com/mttkay/signpost) Matthias Käppler, Signpost OAuth library
  * Shimon Simon (new icon designs)
  * [bbs.goapk.com](http://bbs.goapk.com) / [Chen Ma](https://github.com/marcher233), [Aaron LI](https://github.com/liweitianux) - Chinese translation
  * [Leszek Ciesielski](https://github.com/skolima) - Polish translation
  * Roberto Elena Ormad - Spanish translation
  * Gabriele Ravanetti / [Patryk Rzucidlo](http://www.ptkdev.it/) / [Chiara De Liberato](http://www.chiaradeliberato.it/) - Italian translation
  * Harun Şahin - Turkish translation
  * [Lukas Pribyl](http://www.lukaspribyl.eu) - Czech translation
  * João Pedro Ferreira - Portuguese translation
  * Martijn Brouns, [Niko Strijbol](https://github.com/niknetniko) - Dutch translation
  * [Torbe](https://github.com/Torbe) - Norwegian translation
  * Nemanja Bračko, [Mladen Pejaković](https://github.com/pejakm) - Serbian translation
  * [Markus Osanger](https://github.com/mosanger) - German translation
  * Dimitris / Mazin Hussein - Greek translation
  * [Yunsu Choi](https://github.com/YunsuChoi), [Taegil Bae](https://github.com/demokritos) - Korean translation
  * [Javier Pico](https://github.com/javierpico) - Galician translation
  * [Ferran Rius](https://github.com/Ferri64) - Catalan translation
  * [Mads Andreasen](https://github.com/MadsAndreasen) - Danish translation, initial JobDispatcher code
  * sHa - Ukrainian translation
  * [Erik Eloff](https://github.com/Loffe/) - Swedish translation
  * [Matrix44](https://github.com/Matrix44) - Slovak translation
  * [László Gárdonyi](https://github.com/gLes) - Hungarian translation
  * Petr P. Gornostaev, [saratovout](https://github.com/saratovout) - Russian translation

## <a id="screenhots">Screenshots</a>

### 1.5.11

![SMS Backup+ material][smsbackup_screenshot_material]

### 1.5.10

![SMS Backup+ holo][smsbackup_screenshot_holo]

## License

This application is released under the terms of the [Apache License, Version 2.0][].

[Build Status]: http://travis-ci.com/jberkel/sms-backup-plus
[Build Status SVG]: https://api.travis-ci.com/jberkel/sms-backup-plus.svg?branch=master

[XOAuth2]: https://developers.google.com/gmail/imap/xoauth2-protocol
[changed their API policy]: https://cloud.google.com/blog/products/g-suite/elevating-user-trust-in-our-api-ecosystems
[sensitivity scopes]: https://developers.google.com/gmail/api/auth/scopes
[can no longer use XOAuth2]: https://arstechnica.com/gadgets/2019/06/gmails-api-lockdown-will-kill-some-third-party-app-access-starting-july-15/

[GitHub Community Guidelines]: https://help.github.com/en/articles/github-community-guidelines
[Android Police article]: https://www.androidpolice.com/2019/08/12/sms-backup-is-now-broken-due-to-gmails-api-changes-but-theres-a-workaround/
[Sign in using App Passwords]: https://support.google.com/accounts/answer/185833?hl=en

[Google Play Store]: https://play.google.com/store/apps/details?id=com.zegoggles.smssync
[F-Droid]: https://f-droid.org/packages/com.zegoggles.smssync/

[Use IMAP to check Gmail on other email clients]: https://support.google.com/mail/answer/7126229?hl=en
[converationviewoff]: https://raw.github.com/jberkel/sms-backup-plus/gh-pages/screenshots/soundcloud.com-mail-settings-jan-soundcloud.com.jpg
[imapenableshot]: https://raw.github.com/jberkel/sms-backup-plus/gh-pages/screenshots/enable_imap.png
[showimap]: https://raw.github.com/jberkel/sms-backup-plus/gh-pages/screenshots/show_imap.png
[strings.xml]: https://github.com/jberkel/sms-backup-plus/raw/master/app/src/main/res/values/strings.xml
[Tasker]: https://tasker.joaoapps.com/
[Authorized Access to your Google Account]: https://security.google.com/settings/security/permissions
[Google+ community]: https://plus.google.com/communities/113290889178902750997
[Play Store beta page]: https://play.google.com/apps/testing/com.zegoggles.smssync
[Github releases]: https://github.com/jberkel/sms-backup-plus/releases
[Github issues]: http://github.com/jberkel/sms-backup-plus/issues
[About pull requests]: https://help.github.com/articles/about-pull-requests/

[smsbackup_screenshot_holo]: https://raw.github.com/jberkel/sms-backup-plus/gh-pages/screenshots/smsbackup_holo_cropped.png
[smsbackup_screenshot_material]: https://raw.github.com/jberkel/sms-backup-plus/gh-pages/screenshots/smsbackup_material_cropped.png

[Apache License, Version 2.0]: http://www.apache.org/licenses/LICENSE-2.0.html
[Privacy Policy]: https://jberkel.github.io/sms-backup-plus/privacy-policy/

[516]: https://github.com/jberkel/sms-backup-plus/issues/516
[564]: https://github.com/jberkel/sms-backup-plus/issues/564
[841]: https://github.com/jberkel/sms-backup-plus/issues/841
