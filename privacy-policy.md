---
title: Privacy Policy
layout: default
---

# Privacy Policy for [SMS Backup+][playstore-url]

## Policy Summary (tl;dnr)

SMS Backup+ is a tool to help you back up your text messages and call log
information to an email account.

To function properly it needs to have full access to various types of
private data stored on your phone as well as your email account .

Your data is accessed and then transferred to a place of your choice, usually
to your Gmail account. SMS Backup+ does not store private data on your
phone nor does it send it to any other service except the one defined by you.

Your usage data is not shared with anybody, there are no ads, analytics or other type of
tracking code embedded in the application.

The full [source code][github-url] is public and has been reviewed by
many independent developers over the span of several years.

## Access to personal information

The following personally identifiable information is accessed by SMS Backup+ (also see the
[list of requested permissions](#list-of-requested-permissions) in the
appendix):

* full name
* mobile phone number
* email address
* emails
* contacts (address book)
* text messages
* call logs
* calendar data

The access takes place with the sole purpose of transmitting and storing
selected information to provide you with a convenient backup
of your data. If you choose to store your text messages in Gmail, the
resulting storage and processing is governed by the
[Google Privacy Policy][], see there for details.

If you do not want to store your data in Gmail you have also the option to have
your text messages uploaded to an IMAP server under your control; see the “Advanced”
configuration option and the [FAQ][usage-without-gmail] for more.

## Revoking access

To revoke SMS Backup's access to your data simply delete it and make sure it
is no longer linked to your account. Visit
“[Apps connected to your account][revoke-access]” for details.

## Transactional information

If you decide to make a donation from within the app the following
information is automatically transmitted along with the purchase order:

* post code
* city
* country

The in-app purchase is handled by Google. Please refer to the
[Google Payments Privacy Notice][] for more information about how payment
information is processed and stored.

## Source code

The source code of SMS Backup+ is freely available under a
[Open Source license][code-license], allowing you to verify claims made by
this policy. Around 40 different [developers and volunteers][contributors]
have so far contributed to the project.

To ensure your phone runs the exact version of the code as published,
install SMS Backup+ via the alternative store [F-Droid][f-droid-url].

Alternatively build the package from the source code yourself,
[instructions](installation-from-source) and help are available online.

## Changes

Updates to this privacy policy will be posted at
[this address][this-policy-url], update notifications are made available as
[RSS feed][rss-feed].

## Contact

For any other questions regarding the privacy of your data please contact
me directly.

Contact information as listed in the [Googe Play][playstore-url]:

    Jan Berkel
    Hinkeläcker Str. 35
    67317 Altleiningen

    email: jan.berkel+smsbackup@gmail.com


# Appendix

## List of requested permissions

The permission names correspond to standard codes used by Android. Permissions
marked with ⚠ are classified as “[dangerous permission][normal-and-dangerous-permissions]” by Google.

| Name                          | Scope                                                         | Why needed?
| ----------------------------- | --------------------------------------------------------------|-----------------------------------------
| [RECEIVE_SMS][] ⚠             | Allows to receive SMS messages                                | Trigger backup after message arrival
| [READ_SMS][] ⚠                | Allows to read all SMS messages                               | To backup SMS
| [WRITE_SMS][]                 | Write SMS message (removed in Android 4.4)                    | To restore SMS
| [READ_CALL_LOG][] ⚠           | Allows to read your phone's call log (incoming/outgoing)      | To backup call log
| [WRITE_CALL_LOG][] ⚠          | Allows to write your phone's call log                         | To restore call log
| [READ_CONTACTS][] ⚠           | Allows to read your contact data                              | To map phone numbers to emails
| [WRITE_CONTACTS][] ⚠          | Allows to write your contact data                             | To restore SMS contact data
| [GET_ACCOUNTS][] ⚠            | Allows access to the list of accounts in the Accounts Service | Authenticate with Gmail
| [USE_CREDENTIALS][] ⚠         | Allows to request authentication tokens (removed in Android 6)| Authenticate with Gmail
| [READ_CALENDAR][] ⚠           | Allows to read the your calendar data                         | To restore call log
| [WRITE_CALENDAR][] ⚠          | Allows to write the your calendar data                        | To backup call log
| [ACCESS_NETWORK_STATE][]      | Allows to access information about networks                   | Automatic backup
| [ACCESS_WIFI_STATE][]         | Allows to access information about Wi-Fi networks.            | Automatic backup
| [WRITE_EXTERNAL_STORAGE][] ⚠  | Allows to write to external storage                           | For logging
| [BILLING][]                   | To use the Google Play application                            | optional in-app donation billing
{:.table}

[READ_SMS]: http://androidpermissions.com/permission/android.permission.READ_SMS
[WRITE_SMS]: http://androidpermissions.com/permission/android.permission.WRITE_SMS
[READ_CALL_LOG]: http://androidpermissions.com/permission/android.permission.READ_CALL_LOG
[WRITE_CALL_LOG]: http://androidpermissions.com/permission/android.permission.WRITE_CALL_LOG
[READ_CONTACTS]: http://androidpermissions.com/permission/android.permission.READ_CONTACTS
[WRITE_CONTACTS]: http://androidpermissions.com/permission/android.permission.WRITE_CONTACTS
[RECEIVE_SMS]: http://androidpermissions.com/permission/android.permission.RECEIVE_SMS
[ACCESS_NETWORK_STATE]: http://androidpermissions.com/permission/android.permission.ACCESS_NETWORK_STATE
[ACCESS_WIFI_STATE]: http://androidpermissions.com/permission/android.permission.ACCESS_WIFI_STATE
[GET_ACCOUNTS]: http://androidpermissions.com/permission/android.permission.GET_ACCOUNTS
[READ_CALENDAR]: http://androidpermissions.com/permission/android.permission.READ_CALENDAR
[WRITE_CALENDAR]: http://androidpermissions.com/permission/android.permission.WRITE_CALENDAR
[WRITE_EXTERNAL_STORAGE]: http://androidpermissions.com/permission/android.permission.WRITE_EXTERNAL_STORAGE
[USE_CREDENTIALS]: http://androidpermissions.com/permission/android.permission.USE_CREDENTIALS
[BILLING]: https://developer.android.com/google/play/billing/billing_integrate.html#billing-permission

[playstore-url]: https://play.google.com/store/apps/details?id=com.zegoggles.smssync
[revoke-access]: https://myaccount.google.com/permissions
[usage-without-gmail]: https://github.com/jberkel/sms-backup-plus#usage-without-gmail-imap
[installation-from-source]: https://github.com/jberkel/sms-backup-plus#installation-from-source
[contributors]: https://github.com/jberkel/sms-backup-plus/graphs/contributors
[code-license]: https://github.com/jberkel/sms-backup-plus/blob/master/COPYING
[this-policy-url]: http://jberkel.github.io/sms-backup-plus/privacy-policy
[rss-feed]: http://jberkel.github.io/sms-backup-plus/feed.xml
[normal-and-dangerous-permissions]:https://developer.android.com/guide/topics/permissions/requesting.html#normal-dangerous
[github-url]: https://github.com/jberkel/sms-backup-plus
[f-droid-url]: https://f-droid.org/packages/com.zegoggles.smssync/
[Google Privacy Policy]: https://www.google.com/intl/en/policies/privacy/
[Google Payments Privacy Notice]: https://payments.google.com/payments/apis-secure/get_legal_document?ldo=0&ldt=privacynotice&ldl=en
