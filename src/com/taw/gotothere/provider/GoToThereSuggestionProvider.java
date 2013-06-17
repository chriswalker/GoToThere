/*
 * Copyright 2013 That Amazing Web Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.taw.gotothere.provider;

import android.content.SearchRecentSuggestionsProvider;

/**
 * Basic recent suggestions provider for the search box.
 * 
 * @author Chris
 */
public class GoToThereSuggestionProvider extends SearchRecentSuggestionsProvider {
	
    public final static String AUTHORITY = "com.taw.gotothere.provider.GoToThereSuggestionProvider";
    public final static int MODE = DATABASE_MODE_QUERIES;

    public GoToThereSuggestionProvider() {
        setupSuggestions(AUTHORITY, MODE);
    }

}
