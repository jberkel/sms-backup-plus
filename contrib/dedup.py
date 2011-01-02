#!/usr/bin/env python
"""
  Quick hack to dedup backed up messages on gmail/imap
  Create a file called ~/.netrc with the following:

  machine smsbackup
    login your.account@gmail.com
    password yourpassword

  python dedup.py SMS [label...]
"""

import imaplib
import time
import hashlib
import re
import sys
import os
import logging
from email.parser import Parser
from email.utils import parsedate
from netrc import netrc

class Deduper:
  def __init__(self, login=None, password=None):
    self.logger = logging.getLogger()
    self.logger.addHandler(logging.StreamHandler())
    self.logger.setLevel(logging.DEBUG)

    if login == None and password == None:
      login, account, password = netrc().authenticators('smsbackup')

    self.messages = {}
    self.m = Deduper.connect(login, password)

  @classmethod
  def connect(cls, login, password):
    m = imaplib.IMAP4_SSL('imap.gmail.com', 993)
    #m.debug = 4
    m.login(login, password)
    return m

  @classmethod
  def msg_hash(cls, date, type, address):
    """Same hash as used in sms backup+ (cf. CursorToMessage#createMessageId)"""
    d = hashlib.md5()
    d.update(str(date))
    d.update(address)
    d.update(str(type))
    return d.hexdigest()

  def fetch(self, n):
    """fetch a message by sequence number and return the uid and calculated hash"""
    status, data = self.m.fetch(n, '(UID BODY.PEEK[HEADER.FIELDS (X-SMSSYNC-ADDRESS \
                                                             X-SMSSYNC-TYPE \
                                                             X-SMSSYNC-DATE)])')
    m = re.search(r"UID (\d+)", data[0][0])
    uid = m.group(1)

    parsed = Parser().parsestr(data[0][1], True)

    date, type, address = (int(parsed.get('x-smssync-date')),
      int(parsed.get('x-smssync-type')),
      parsed.get('x-smssync-address'))

    return uid, Deduper.msg_hash(date, type, address)

  def check_label(self, label):
    self.logger.info("checking label: %s", label)
    status, count = self.m.select(label, readonly=True)

    if status == 'OK':
      for i in range(1, int(count[0])+1):
        uid, h = self.fetch(i)
        self.messages.setdefault(h, []).append([label, uid])
    else:
      raise Exception("Error", status)

  def dups(self):
    dups = []
    for k,v in self.messages.items():
      if len(v) > 1:
        dups.append(self.filter(v))

    return dups

  def message_count(self):
    count = 0
    for v in self.messages.values():
      count += len(v)
    return count


  def print_body(self):
    """used for debugging. dump bodies and relevant headers of suspected dups"""
    for dup in self.dups():
      for (label, uid) in dup:
        self.m.select(label, readonly=True)
        status, data = self.m.uid('FETCH', uid,
          '(BODY.PEEK[TEXT] BODY.PEEK[HEADER.FIELDS (X-SMSSYNC-ADDRESS \
                           X-SMSSYNC-TYPE \
                           X-SMSSYNC-DATE)])')
        if status == 'OK':
          for d in data:
            if len(d[1:]) > 0:
              self.logger.debug("uid %s: %s", uid, d[1:])
        else:
          raise Exception("Error", status)

  def to_json(self, filename = 'messages.json'):
    import json
    file = open(filename, 'w')
    try:
      json.dump(self.messages, file, sort_keys=True, indent=4)
    finally:
      file.close()

  def from_json(self, filename = 'messages.json'):
    import json
    file = open(filename, 'r')
    try:
      self.messages = json.load(file)
    finally:
      file.close()

  def search(self, label):
    self.m.select(label, readonly=True)
    status, data = self.m.search(None, 'UNDELETED',  '(OR HEADER X-SMSSYNC-DATATYPE "SMS" HEADER X-SMSSYNC-DATATYPE "MMS")')
    if status == 'OK':
      print "OK:" + str(data)
    else:
      raise Exception("Error", status)

  def delete(self, dryrun = False):
    """deletes all found duplicates"""
    deleted = 0
    for dup in self.dups():
      for (label, uid) in dup:
        self.m.select(label, readonly=False)
        deleted += 1

        if dryrun:
          self.logger.debug("would delete %s/%s", label, uid)
        else:
          status, data = self.m.uid('STORE', uid, '+FLAGS', r"\Deleted")
          if status == 'OK':
             self.m.expunge()
             self.logger.debug("deleted %s/%s", label, uid)
          else:
             raise Exception("Error", status)

    self.logger.debug("deleted %d/%d messages", deleted, self.message_count())
    return deleted

  def filter(self, dups):
    return dups[1:]

  def logout(self):
      self.m.logout()

if __name__ == "__main__":
  if len(sys.argv) < 2:
    print "%s <label...>" % os.path.basename(sys.argv[0])
    sys.exit(1)

  d = Deduper()
  for label in sys.argv[1:]:
    #d.search(label)
    d.check_label(label)

  #d.to_json()
  #d.print_body()
  d.delete(dryrun = False)

  d.logout()
