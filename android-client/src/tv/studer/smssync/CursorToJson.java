/* Copyright (c) 2009 Christoph Studer <chstuder@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tv.studer.smssync;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.database.Cursor;

/**
 * Converts the data of a cursor into an JSONArray.
 * 
 * @author Christoph Studer (chstuder@gmail.com)
 */
public class CursorToJson {

    public static JSONArray cursorToJsonArray(Cursor cursor, int maxEntries) throws JSONException {
        JSONArray result = new JSONArray();

        String[] columns = cursor.getColumnNames();
        while (cursor.moveToNext()) {
            JSONObject obj = new JSONObject();
            for (int i = 0; i < columns.length; i++) {
                obj.put(columns[i], cursor.getString(i));
            }
            result.put(obj);
            if (result.length() == maxEntries) {
                // Only consume up to 'maxEntries' items.
                break;
            }
        }

        return result;
    }
}
