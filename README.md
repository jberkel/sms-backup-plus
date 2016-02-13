## SMS Backup+

[![][Icon]][PlayLink]
[![Build Status PNG][]][Build Status]

This is a fork of the Android backup tool
[SMS Backup](http://code.google.com/p/android-sms), where development has
stopped a while ago. It uses Gmail to perform SMS, MMS and call log backups over
the network.

Main differences / improvements:

  * New restore feature. SMS stored on Gmail can be transferred back to the
  phone. This even works for users who have already created their backups with
  older versions of SMS Backup. Note: MMS are currently not restored.

  * XOAuth: SMS Backup+ will never ask you for your Gmail password. Instead it
  uses [XOAuth](http://code.google.com/apis/gmail/oauth/) to get access to your
  data. You can revoke the access rights at any time.

  * MMS backup support (since `1.1`), only available on Android 2.x

  * Call log backup (since `1.2`), with Google Calendar integration (since
  `1.3.`) and restore (since `1.4`).

  * Batch size limits removed.

  * Works with any IMAP server (but defaults to Gmail).

Tested with Android 2.x - 6.0.x.

SMS Backup+ is available for free in the Google Play Store, there will never be a pro / paid version.

But if you find the app useful and want to support the development of it you can make a donation
from within the app itself, using the secure Play Store payment mechanism.

To get updates more frequently join the [beta programme](#beta) or download the latest beta manually
from [releases][].

## <a name="usage">Usage</a>

### Installation

![PlayQRCode][] Install via the Android QR code link, [Google Play][PlayLink], [f-droid][].

### First steps

You need to have an IMAP account or a Gmail account with IMAP enabled. See the
[Enabling IMAP in Gmail][] help page to learn how to enable IMAP for your Gmail
account or look at this [screenshot][imapenableshot].

After starting SMS Backup+, tap on the "Connect" check box to start the
authorization process.

On newer Android devices you'll be presented with a list of Gmail accounts to
be used for the backup. You'll need to give SMSBackup+ the permission to access your
emails.

On older devices a browser window will open where Gmail prompts you to log in and asks you to give
SMS Backup+ permission to access your emails.

After selecting "Grant Access" SMS Backup+ will become visible again, the
checkbox should now be checked, indicating that the authorization process was
successful.

### Initial backup

It is important that you perform the first backup manually. SMS Backup+ needs
to know whether you want to upload messages currently stored on your device or
not.

After having connected your Gmail account, SMS Backup+ will ask you to perform
a first sync. If you choose "Backup", SMS Backup+ will start backing up all
your messages to Gmail.

If you choose "Skip", nothing is sent to Gmail and all messages currently
stored on your device are simply marked "backed up". This option is handy if
you previously uninstalled SMS Backup+ and do not want to send your messages
again to Gmail. Please note that any messages arrived after you last
uninstalled SMS Backup and this initial backup won't ever be backed up to
Gmail.

### Restoring

If you wish to restore messages back to your phone tap "Restore". By default
all messages stored on Gmail will be restored (this can be changed in "Advanced
Settings"). You can safely restore to a phone which has already message stored
on it, SMS Backup+ will skip the restore of already existing messages.

### <a name="call-log-support">Call log support</a>

SMS Backup+ can also backup and restore your call logs. It stores all calls using a
separate label (defaults to `Call log`, but can be changed in Advanced
Settings). If you wish you can set this to the same value as `SMS`, to make all backed
up data use the same label.

The body of the call log message contains the duration of the
call in seconds followed by the phone number and call type (`incoming` /
`outgoing` / `missed`).

An example:

    267s (00:04:07)
    +44123456789 (incoming call)

You can also add call log entries to a Google calendar. Just select `Calendar
sync` in Advanced settings, and make sure you have selected a calendar to sync
with.

If you only want to backup specific call types you can do so as well.

### <a name="whatsapp-support">WhatsApp support</a>

Please don't file bug reports against this, WhatsApp backup support is no longer supported or
included in the latest version. If you really need it you can manually install an older version which still includes the feature,
[1.5.9-BETA5](https://github.com/jberkel/sms-backup-plus/releases/tag/1.5.9-BETA5). Also see issue [564][].

### <a name="3rdparty">3rd party app integration</a>

If you want to trigger backups from another app, enable `3rd party integration`
in Advanced Settings and send the broadcast intent
`com.zegoggles.smssync.BACKUP`. This will work even when Auto Backup is
disabled.

### Usage without Gmail (IMAP)

You don't have to use Gmail to backup your text messages - change
Authentication to Plain text in "Advanced Settings - IMAP server settings",
then change the server address / username and password accordingly. Make sure
to set security to "SSL (optional / trust all)" if the IMAP server you're connecting to has
a self-signed certificate ("Unknown certificate" error during backup).

Also note that Gmail labels correspond to IMAP folders, which will
automatically be created the first time you're performing a backup.

## <a name="faq">FAQ</a>

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
    * [My messages get backed up as calls!](#faq-backup-sms-as-calls)
    * [I get the error "Trust anchor for certification path not found"](#faq-backup-untrusted-certificate)
  * [Restore questions](#faq-restore)
    * [Why does SMS Backup+ ask to become the default SMS app?](#faq-restore-default-app)
    * [Are there any plans to support restoring of MMS?](#faq-restore-MMS)
    * [I'm not able to restore all of my 20000 messages!](#faq-restore-many-messages)
    * [How do I restore the last N weeks / N messages?](#faq-restore-partial)
    * [The timestamps of the restored messages is wrong / the messages are not restored in the right order](#faq-restore-reversed)
  * [Authentication questions](#faq-authentication)
    * [How can I revoke the app's access to my Gmail account?](#faq-authentication-revoke-access)
    * [When connecting, I get 'Could not obtain request token...'](#faq-authentication-request-token)
  * [Device specific questions](#faq-device-specific)
    * [I'm using a Motorola DROID X/2, and it does not back up incoming messages, only sent!](#faq-device-specific-droidx-received)

### <a name="faq-general">General questions</a>

#### <a name="faq-general-file-bug-report">I want to file a bug report, what should I do?</a>

First check [github issues][] to see if the bug has already been reported. If not, create a new
issue and attach the following details:

 * Version of SMS Backup+ used
 * Version of Android / brand of phone used

If it is related to backing up / restoring you should also enable the sync log in debug mode
(Advanced settings) and attach a relevant portion of it. The sync log is located on your SD card as
"sms_backup_plus.log".

To attach the sync log create a "gist" (https://gist.github.com) and link to the gist you created instead
of posting the full content in the issue.

It might also be worth to install the current <a href="#beta">beta</a> version of SMS Backup+ to
make sure the bug has not already been fixed.

#### <a name="faq-general-can-you-add-feature-x">Can you add feature X?</a>

Over the years a lot of features have been added, often as a result of
requests by users. This has worked great initially but has made the product itself very unfocussed
and generic. It started as a tool to back up text messages (as the name *SMS* Backup implies) but
gradually more and more features were added (call logs, MMS, WhatsApp...). It's now at a point where
it has become too heavy and difficult too maintain or use. The settings screen makes this obvious,
there are just too many things to configure. If anything features should be removed at this point,
not added. A more focussed product would be easier to maintain and use.

Right now, SMS Backup+ is in maintenance mode; no new features will be added. Existing bugs will of
course be addressed.

#### <a name="faq-general-permissions">Why does it need so many permissions?</a>

  * Read contacts - Needed to map phone numbers to names and email addresses
  * Your messages (read / write SMS) - Needed for backup+restore
  * Prevent phone from sleeping - needed to keep network active during a backup
  * Modify calendar events - needed for the call log backup to GCal
  * Send email to guests - this refers to calendar invitations (which are not created by the app)
  * Storage (modify/delete SD card contents) - this is needed for caching
  * Find accounts on the device - used for authentication
  * Use accounts on the device - used for authentication
  * Google Play billing service - used for in-app donations
  * Run at startup - used to enable automatic backups after reboot

#### <a name="faq-general-package-not-signed">When updating the app I get 'Package file was not signed correctly'</a>

Try uninstalling the app, then installing the new version. Make sure to select
"Skip" when doing the first backup, otherwise messages will get backed up
twice.

### <a name="faq-backup">Backup questions</a>

#### <a name="faq-backup-automatic-backup">Automatic backup does not work / stopped working</a>

If the automatic backup does not work first make sure that a manually
initiated backup works as expected. There seem to be some problems with
automatic backups; a fix will be to change the current backup logic to use
Android's built-in SyncManager: see github tickets [507][] and [572][].

This change will be part of the next major version ([1.6.0][], no release date yet).

#### <a name="faq-backup-show-imap">I get the one of following errors during backup/restore: Command: SELECT "SMS"; response: \#6\# \[NO, Unknown, Mailbox; SMS, \[Failure\]\] (or response: \#6\# \[NO, \[NONEXISTENT\], unknown mailbox: SMS (failure)\])</a>

Make sure you have the "Show IMAP" option checked in the Gmail label settings:

![Screenshot][showimap]

If this is the case make sure that the label name is set correctly (capitalization
matters!).

#### <a name="faq-backup-reset">How can I make the app think that it has to do the backup again?</a>

Select "Reset" from the menu, and confirm that you want to reset the current
sync state. All messages on the phone will be backed up on the next run.

#### <a name="faq-backup-only-received">Only received messages are backed up, not the ones I sent</a>

Do you use Google Voice to send messages? There is an open issue: [516][]. It could also be
a [device specific problem](#faq-device-specific).

#### <a name="faq-backup-inbox">Why do backed up SMS show up in my inbox?</a>

This is probably related to Gmail's automatic priority inbox filing.
A workaround is to set up a filter with "subject: SMS with", let the filter
mark it as not important.

![](https://raw.github.com/jberkel/sms-backup-plus/gh-pages/screenshots/20120106-tymk3rn4i5apshhr6e1hbd17qn.jpg)
![](https://raw.github.com/jberkel/sms-backup-plus/gh-pages/screenshots/20120106-rsg7912rnus5gwe3e572rxwbae.jpg)

#### <a name="faq-backup-to-inbox">I want the backed up messages to show up in my Gmail inbox!</a>

Just set the label to "Inbox" instead of the default "SMS" or "Call log".

#### <a name="faq-backup-schedule">What's the difference between regular and incoming backup schedule?</a>

Incoming backup schedule is used for incoming messages. 3 minutes here means
that any incoming SMS will trigger a backup after 3 minutes. It is a full
backup (including any sent messages). You should set the incoming schedule to a
low value if you want to make sure that incoming SMS show up in
Gmail shortly after arrival.

Regular schedule is used to perform backups in specific intervals. 2 hours here
means that the device will try to backup all messages every 2 hours.

Fewer updates performed by the app means less energy consumed, so there's
a trade-off data protection vs. battery life.

#### <a name="faq-backup-scheduling">I'd like SMS Backup+ to schedule a backup only at a given time of the day / when Wifi is available / etc.</a>

If you require more control over the backup schedule than what SMS Backup+ already
provides you can use a 3rd party app to trigger the backup. [Tasker][] for
example supports SMS Backup+ since version 1.0.14.

#### <a name="faq-backup-gmail-100">The app saves only 100 SMS/MMS per contact!</a>

This seems to be a limitation of Gmail. After the first hundred or so SMS being
backed up, Gmail will cease to properly thread many of the conversations.
You will notice that Gmail will eventually treat each SMS (in that initial
backup) as individual conversations and will not longer group/thread them
together.

A way around this is to do a full backup 100 SMS at a time (see `Advanced
settings`).

#### <a name="faq-backup-threading">In Gmail, I'd like to have all messages listed chronologically and not ordered by who sent them.</a>

It's a Gmail feature, but you can disable it.
In Gmail settings, set conversation view to `off`
([screenshot][converationviewoff]).

<a name="faq-delete"/>
#### <a name="faq-backup-does-it-sync">When I delete a text locally, will it delete the saved copy on Gmail?</a>

No. SMS Backup+ does not do a "real" sync, once the text has been transferred
to Gmail it won't get modified or deleted by the app.

#### <a name="faq-backup-sms-as-calls">My messages get backed up as calls!</a>

This might be due to some changes in Samsung's version of Android, see [#287](https://github.com/jberkel/sms-backup-plus/issues/287).

#### <a name="faq-backup-untrusted-certificate">I get the error "Trust anchor for certification path not found"</a>

This means that it is impossible to establish a trusted connection with the IMAP server. A few
reasons why this might happen:

  * You run an old version of Android
  * You use an IMAP server with an expired / self-signed certificate

You can try to set the IMAP Server Setting `Security` to `SSL (optional / trust all)`.

### <a name="faq-restore">Restore questions</a>

#### <a name="faq-restore-default-app">Why does SMS Backup+ ask to become the default SMS app?</a>

Google has changed the way SMS permissions work starting with Android KitKat. In order
to get write access to your messages an app has to be set as the default system SMS app. Before starting
the restore operation SMS Backup+ will request your permission to be set as default app. After the
restore has finished you will be asked to set the previous choice (usually 'Messaging')
back as default. This last step is important, if you don't set the old default back you might lose
new messages.

#### <a name="faq-restore-MMS">Are there any plans to support restoring of MMS?</a>

No, for a variety of reasons: MMS are highly carrier-dependent, documentation is lacking and the
Android Emulator does not support them.

However SMS Backup+ is open source; patches are more than welcome.

#### <a name="faq-restore-many-messages">I'm not able to restore all of my 20000 messages!</a>

SMS Backup has not been designed to restore many thousands of messages. See the question "[How do I restore the last N weeks / N messages?](#faq-restore-partial)" for a way around that.

#### <a name="faq-restore-partial">How do I restore the last N weeks / N messages?</a>

If you have a lot of messages backed up (let's say over 5000) restoring can be
very slow, especially if you're only interested in the most recent messages.

A workaround is to use the Gmail web interface (or an IMAP email client) to
move the bulk of the messages to another label in Gmail (e.g. SMSARCHIVED), and
only keep a few hundred or so messages in the SMS label.

Next time you restore it will only restore those messages and it will be a lot
faster.

#### <a name="faq-restore-reversed">The timestamps of the restored messages is wrong / the messages are not restored in the right order</a>

This is a known bug: [#94](https://github.com/jberkel/sms-backup-plus/issues/94)

### <a name="faq-authentication">Authentication questions</a>

#### <a name="faq-authentication-revoke-access">How can I revoke the app's access to my Gmail account?</a>

Go to [Authorized Access to your Google Account][] and select "Revoke Access"
next to "SMS Backup+".

#### <a name="faq-authentication-request-token">When connecting, I get 'Could not obtain request token...'</a>

If you get this error message and your network connection is active
double-check that your time zone settings are correct, and that the local time is
displaying correctly. The authentication process won't work otherwise.

### <a name="faq-device-specific">Device specific questions</a>

#### <a name="faq-device-specific-droidx-received">I'm using a Motorola DROID X/2, and it does not back up incoming messages, only sent!</a>

It's a known SMS bug in the latest OTA 2.2 update ([details][droidbug]). As a workaround
you can try installing [SMS Time fix][] ([apk][smstimefixzip]) and set "Adjustment Method" to "Use Phone's Time".


## <a name="beta">Beta testing

If you want to help beta testing, join the [community][] on Google+ and follow the "Become a beta
tester" link in the "About this community box". You will be asked to opt in to the beta program (you
can leave it at any time).

Once opted in your device will automatically update to the latest beta which might have bug fixes
and features not found in the currently released version.

Alternatively, if for some reason you don't want to join the community you can download an APK and
install it manually from [Github releases][releases] (however, you won't get automatic updates this
way).

## <a name="contributing">Contributing</a>

### Installation from source

    $ git clone git://github.com/jberkel/sms-backup-plus.git
    $ cd sms-backup-plus
    $ mvn install
    $ adb install target/smsbackup-plus-1.x.y-SNAPSHOT.apk

I've imported some relevant issues from the [original issue list][] to [github issues][].

### <a name="translating">Translating the UI</a>

If you want to help translating the UI to other languages download and
translate the following file, then send the translated version via email:

  * [strings.xml][]

However, if you're already familiar with Git I'd prefer if you cloned the
repository and send me a [pull request][].

##<a name="credits">Credits</a>

  * [Christoph Studer](http://studer.tv/) Original author of SMS Backup
  * [Ben Dodson](http://github.com/bjdodson) - Contacts 2.0 / MMS support
  * [Felix Knecht](http://github.com/dicer) - Call log backup code
  * [Michael Scharfstein](http://github.com/smike) - Call log calendar ICS support
  * [k9mail](http://code.google.com/p/k9mail/) IMAP library, with some modifications ([k9mail/sms-backup-plus](https://github.com/jberkel/k-9/tree/sms-backup-plus))
  * [signpost](http://github.com/kaeppler/signpost) Signpost OAuth library
  * Shimon Simon (new icon designs)
  * [bbs.goapk.com](http://bbs.goapk.com) / [Chen](http://blog.thisischen.com/) - Chinese translation
  * [skolima](http://github.com/skolima) - Polish translation
  * Roberto Elena Ormad - Spanish translation
  * Gabriele Ravanetti / [Patryk Rzucidlo](http://www.ptkdev.it/) / [Chiara De Liberato](http://www.chiaradeliberato.it/) - Italian translation
  * Harun Sahin - Turkish translation
  * [Lukas Pribyl](http://www.lukaspribyl.eu) - Czech translation
  * João Pedro Ferreira - Portuguese translation
  * Martijn Brouns - Dutch translation
  * [Tobeon](http://tobeon.net) - Norwegian translation
  * Nemanja Bračko - Serbian translation
  * Markus Osanger - German translation
  * Dimitris / Mazin Hussein - Greek translation
  * Yunsu Choi - Korean translation
  * Javier Pico - Galician translation
  * Ferran Rius - Catalan translation
  * Mads Andreasen - Danish translation
  * sHa - Ukrainian translation

##<a name="screenhots">Screenshots</a>

![SMS Backup+ screenshot][smsbackupshot]

##<a name="license">License</a>

This application is released under the terms of the [Apache License, Version 2.0][].

[apk]: https://github.com/jberkel/sms-backup-plus/releases/download/1.5.9/smsbackup-plus-1.5.9-market.apk
[original issue list]: http://code.google.com/p/android-sms/issues/list
[github issues]: http://github.com/jberkel/sms-backup-plus/issues
[PlayQRCode]: http://chart.apis.google.com/chart?cht=qr&chs=100x100&chl=https://play.google.com/store/apps/details?id=com.zegoggles.smssync
[f-droid]: https://f-droid.org/repository/browse/?fdid=com.zegoggles.smssync
[PlayLink]: https://play.google.com/store/apps/details?id=com.zegoggles.smssync
[Enabling IMAP in Gmail]: http://mail.google.com/support/bin/answer.py?hl=en&answer=77695
[smsbackupshot]: https://raw.github.com/jberkel/sms-backup-plus/gh-pages/screenshots/smsbackup_holo_cropped.png
[droidbug]: http://www.mydigitallife.info/2010/09/27/motorola-droid-x-froyo-text-messaging-bug-rectified-via-sms-time-fix/
[SMS Time fix]: http://www.appbrain.com/app/sms-time-fix/com.mattprecious.smsfix
[converationviewoff]: https://raw.github.com/jberkel/sms-backup-plus/gh-pages/screenshots/soundcloud.com-mail-settings-jan-soundcloud.com.jpg
[smstimefixzip]: https://supportforums.motorola.com/servlet/JiveServlet/download/269690-40815/sms-time-fix.zip
[imapenableshot]: https://raw.github.com/jberkel/sms-backup-plus/gh-pages/screenshots/enable_imap.png
[showimap]: https://raw.github.com/jberkel/sms-backup-plus/gh-pages/screenshots/show_imap.png
[strings.xml]: https://github.com/jberkel/sms-backup-plus/raw/master/res/values/strings.xml
[Tasker]: http://tasker.dinglisch.net/
[Tri-Crypt]: https://play.google.com/store/apps/details?id=com.tricrypt
[Icon]: https://raw.githubusercontent.com/jberkel/sms-backup-plus/master/res/drawable/ic_launcher.png
[Authorized Access to your Google Account]: https://security.google.com/settings/security/permissions
[community]: https://plus.google.com/communities/113290889178902750997
[releases]: https://github.com/jberkel/sms-backup-plus/releases
[pull request]: https://help.github.com/articles/using-pull-requests/
[Build Status]: http://travis-ci.org/jberkel/sms-backup-plus
[Build Status PNG]: https://secure.travis-ci.org/jberkel/sms-backup-plus.png?branch=master
[Apache License, Version 2.0]: http://www.apache.org/licenses/LICENSE-2.0.html
[507]: https://github.com/jberkel/sms-backup-plus/issues/507
[516]: https://github.com/jberkel/sms-backup-plus/issues/516
[564]: https://github.com/jberkel/sms-backup-plus/issues/564
[572]: https://github.com/jberkel/sms-backup-plus/issues/572
[1.6.0]: https://github.com/jberkel/sms-backup-plus/milestones/1.6.0
