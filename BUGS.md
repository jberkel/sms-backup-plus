# Known Issues

If your problem is connected any of the following issues, please add your comments to the existing issues rather than creating new ones.

1. **Can't automatically sign into Gmail**. A 2018 policy change now requires applications to be manually vetted to get access to Gmail using XOAUTH, with only "email client" applications being eligible. `IMAP` access remains unaffected but you will need to manually enable it on your Gmail account.
  The issue is being discussed heavily in the [issue tracker](https://github.com/jberkel/sms-backup-plus/issues/959).
  * For a workaround, please refer to the instructions referenced in the
    [issue tracker](https://github.com/jberkel/sms-backup-plus/issues/959#issuecomment-513018820)
    or this [Android Police article](https://www.androidpolice.com/2019/08/12/sms-backup-is-now-broken-due-to-gmails-api-changes-but-theres-a-workaround/).
  * If you wish to backup to a non-Gmail address, follow the instructions referenced
    [here](https://github.com/jberkel/sms-backup-plus/issues/959#issuecomment-507385460)
  * Some comments suggest that you can work around this limitation by signing into Google via another app such as Chrome; this is fragile as it relies in bugs that may be fixed at any time. Instead please use the `IMAP` access method as explained in #959.
  * Never tell `SMS Backup+` your main Gmail password; instead, create an "application password" and use that instead.
2. **Saving phone calls as Calendar events doesn't work** for some users.
  The symptoms including being unable to choose a calendar, or having events recorded in the wrong calendar, or being unable to enable or disable recording of calendar events.
  See issue #963 for details.
3. **Misattribution of MMS**. Some devices swap the sender and receiver of MMS messages when presenting them to the app; this may be consistent or random depending on the device. This results in a range of issues from the obvious "sender and receiver are swapped", to failure to keep conversations threaded properly, and (we suspect) some cases where one side of a conversation seems to disappear entirely.
4. **No messages backed up**. There is a setting intended to restrict the number of messages that will be backed up or restored in a single session. A recent change to Android means that if this setting is anything other than `unlimited`, the backup or restoration will completely fail. Please ensure it is set to `unlimited` before reporting bugs for "no messages backed up" or "no messages restored".

Please understand that github issues are intended to provide information to assist with fixing bugs or improving documentation. As such, please keep comments focused on resolving the issue, as well as following the [GitHub Community Guidelines](https://help.github.com/en/articles/github-community-guidelines).

Issues that are duplicates of earlier issues may be closed and merged with those issues.
