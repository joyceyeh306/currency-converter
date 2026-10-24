#pragma once
#include <cstdint>
#include <cmath>

// 059 deliberately uses two INDEPENDENT Whisper signals, not a sound-level
// detector: wind and metal noises are not reliable silence indicators.
// Extremely conservative. Never delete a quiet/repeated genuine utterance
// merely because it is short, quiet, repeated, or near the end of the file.
namespace ajo_silence059 {
constexpr int64_t MAX_AUDIO_MS=90000;
constexpr int MAX_REVIEWS=2;
inline bool shouldReview(int64_t startMs,int64_t endMs,int64_t audioLengthMs,
                         int chars,float noSpeech,float tokenProb) {
    if(audioLengthMs<=0 || audioLengthMs>MAX_AUDIO_MS || startMs<0 ||
       endMs<=startMs || endMs>audioLengthMs+500 ||
       endMs-startMs<700 || endMs-startMs>8000 || chars<3) return false;
    return std::isfinite(noSpeech) && std::isfinite(tokenProb) &&
           noSpeech>=0.82f && tokenProb<0.42f;
}
inline bool shouldDropAfterRecheck(int result,int independentSegments,
                                  float noSpeech,float tokenProb) {
    // A failed/cancelled second pass is NEVER evidence to delete subtitles.
    return result==0 && independentSegments==0 &&
           std::isfinite(noSpeech) && std::isfinite(tokenProb) &&
           noSpeech>=0.86f && tokenProb<0.40f;
}
}
