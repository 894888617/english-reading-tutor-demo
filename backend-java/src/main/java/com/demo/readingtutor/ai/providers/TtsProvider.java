package com.demo.readingtutor.ai.providers;

import com.demo.readingtutor.ai.types.AiTypes.TtsRequest;
import com.demo.readingtutor.ai.types.AiTypes.TtsResult;

public interface TtsProvider {
    TtsResult synthesize(TtsRequest input);
}
