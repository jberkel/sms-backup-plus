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
    

list_query_date = Message.gql("WHERE user = :1 AND date <= :2 ORDER BY date DESC LIMIT 100")
list_query = Message.gql("WHERE user = :1 ORDER BY date DESC LIMIT 100")

class ListHandler(webapp.RequestHandler):
  
  def get(self):
    user = users.get_current_user()
    if not user:
      self.response.set_status(401, 'Login required.')
      return
    
    max_date = self.request.get("max_date")
    if max_date:
      list_query_date.bind(user, max_date)
      q = list_query_date
    else:
      list_query.bind(user)
      q = list_query
    
    self.response.out.write('<table>')
    self.response.out.write('<tr><td>Date</td><td>From</td><td>Message</td>')
    for msg in q:
      self.response.out.write('<tr><td>')
      self.response.out.write(msg.date)
      self.response.out.write('</td><td>')
      self.response.out.write(msg.address)
      self.response.out.write('</td><td>')
      self.response.out.write(msg.body)
    self.response.out.write('</td></tr></table>')
    