#!/usr/bin/env python
"""
  Quick hack to dedup backed up messages on gmail
  Create a file called ~/.netrc with the following:

  machine google.com
    login your.account@gmail.com
    password yourpassword
"""

import imaplib
import time
import hashlib
import re
import sys
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
      login, account, password = netrc().authenticators('google.com')

    self.messages = {}
    self.m = Deduper.connect(login, password)

  @classmethod
  def connect(cls, login, password):
    m = imaplib.IMAP4_SSL('imap.gmail.com', 993)
    m.login(login, password)
    return m

  @classmethod
  def msg_hash(cls, date, type, address):
    d = hashlib.md5()
    d.update(str(date))
    d.update(address)
    d.update(str(type))
    return d.hexdigest()

  def fetch(self, n):
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
    for (k,v) in self.messages.items():
      if len(v) > 1:
        dups.append(self.filter(v))

    return dups

  def print_body(self):
    for dup in self.dups():
      for (label, uid) in dup:
        self.m.select(label, readonly=True)
        status, data = self.m.uid('FETCH', uid, '(BODY[TEXT])')
        if status == 'OK':
          self.logger.debug("data: %s", data)
        else:
          raise Exception("Error", status)

  def delete(self, ids):
    for (label, uid) in ids:
      self.m.select(label, readonly=False)
      status, data = self.m.uid('STORE', uid, '+FLAGS', r"\Deleted")
      if status == 'OK':
         self.m.expunge()
         print "uid", uid, "deleted"
      else:
         raise Exception("Error", status)

  def filter(self, dups):
    return dups
    #return dups[1:]

  def logout(self):
      self.m.logout()

if __name__ == "__main__":
  d = Deduper()
  d.check_label('SMSRenamed')
  d.print_body()
  d.logout()
