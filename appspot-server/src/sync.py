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

import logging
from datetime import datetime
import wsgiref.handlers
from google.appengine.ext import db
from google.appengine.api import users
import simplejson as json
import model
from model import Message

from google.appengine.ext import webapp
    
class SyncHandler(webapp.RequestHandler):
  
  def post(self):
    self.response.headers.add_header('Content-Type', 'application/json')
    
    # Read JSON request and decode into dictionary
    request = json.loads(self.request.body)
    
    # Retrieve SyncClient from request. Check for validity.
    syncClientId = request['syncClientId']
    syncClient = db.get(db.Key(syncClientId))
    if not syncClient:
      logging.error('Invalid sync client.')
      self.response.set_status(500, 'Unknown syncClientId.')
      return
    if syncClient.user <> users.get_current_user():
      logging.error('Trying to sync using a client that does not belong to this user.')
      self.response.set_status(500, 'Invalid syncClientId for this user.')
      return
      
    # Retrieve messages from request.
    currentTime = datetime.utcnow()
    newMessages = request['messages']
    maxId = -1
    # Loop over messages and store into datastore.
    for jsonMsg in newMessages:
      logging.info(jsonMsg)
      longId = long(jsonMsg['_id'])
      if (longId > maxId):
        maxId = longId
      msg = Message(
        user=users.get_current_user(),
        syncClient=syncClient,
        syncTime=currentTime,
        originalId=jsonMsg['_id'],
        type=long(jsonMsg['type']),
        status=long(jsonMsg['status']),
        body=jsonMsg['body'],
        address=jsonMsg['address'],
        date=datetime.utcfromtimestamp(long(jsonMsg['date']) / 1000),
        read=bool(jsonMsg['read']),
        fullJson=json.dumps(jsonMsg, ensure_ascii=False),
        appVersion=request['appVersion']);
      db.put(msg)
    
    response = dict()
    
    response['maxConfirmedId'] = str(maxId)
    self.response.out.write(response)

