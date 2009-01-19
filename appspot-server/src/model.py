#!/usr/bin/env python
#
# Copyright 2009 Christoph Studer <chstuder@gmail.com>
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

from google.appengine.api import users
from google.appengine.ext import db

class Message(db.Model):
  user = db.UserProperty(required=True)
  syncTime = db.DateTimeProperty(required=True)
  syncClient = db.ReferenceProperty(required=True)
  
  originalId = db.StringProperty()
  type = db.IntegerProperty()
  status = db.IntegerProperty()
  date = db.DateTimeProperty()
  address = db.StringProperty()
  body = db.TextProperty()
  read = db.BooleanProperty()
  
  fullJson = db.TextProperty()
  appVersion = db.StringProperty()

class SyncClient(db.Model):
  user = db.UserProperty(required=True)
  regTime = db.DateTimeProperty(required=True)
