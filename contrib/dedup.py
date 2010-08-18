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
from email.parser import Parser
from email.utils import parsedate
from netrc import netrc

def hash(date, type, address):
  d = hashlib.md5()
  d.update(str(date))
  d.update(address)
  d.update(str(type))
  return d.hexdigest()

def connect(login, password):
  m = imaplib.IMAP4_SSL('imap.gmail.com', 993)
  m.login(login, password)
  return m

def fetch(m, n):
  status, data = m.fetch(n, '(UID BODY.PEEK[HEADER.FIELDS (X-SMSSYNC-ADDRESS \
                                                           X-SMSSYNC-TYPE \
                                                           X-SMSSYNC-DATE)])')
  m = re.search(r"UID (\d+)", data[0][0])
  uid = m.group(1)

  parsed = Parser().parsestr(data[0][1], True)

  date, type, address = (int(parsed.get('x-smssync-date')),
    int(parsed.get('x-smssync-type')),
    parsed.get('x-smssync-address'))

  return uid, hash(date, type, address)

def dedup(label_list = ['SMS']):
  login, account, password = netrc().authenticators('google.com')
  m = connect(login, password)

  for label in label_list:
    print "checking label:", label
    status, count = m.select(label, readonly=True)

    items = {}
    for i in range(1, int(count[0])+1):
      uid, h = fetch(m, i)
      items.setdefault(h, []).append(uid)

  for (k,v) in items.items():
    if len(v) > 1:
      print "dups:", [k, v]

  m.close()
  m.logout()

dedup()
