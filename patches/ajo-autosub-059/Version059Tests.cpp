#include "../app/src/main/cpp/whisper_silence_review059.h"
#include <cassert>
#include <iostream>
int main() {
    using namespace ajo_silence059;
    assert(MAX_REVIEWS==2 && MAX_AUDIO_MS==90000);
    assert(shouldReview(30000,32000,38290,4,.95f,.22f));
    assert(!shouldReview(30000,32000,38290,4,.08f,.90f));
    assert(!shouldReview(6000,6800,38290,4,.80f,.39f));
    assert(!shouldReview(30000,32000,38290,4,.95f,.85f));
    assert(!shouldReview(1000,1400,38290,2,.99f,.05f));
    assert(!shouldReview(30000,32000,120000,4,.99f,.05f));
    assert(!shouldReview(30000,42000,60000,4,.99f,.05f));
    assert(!shouldDropAfterRecheck(-1,0,.97f,.01f));
    assert(!shouldDropAfterRecheck(0,1,.97f,.01f));
    assert(!shouldDropAfterRecheck(0,0,.75f,.01f));
    assert(!shouldDropAfterRecheck(0,0,.97f,.45f));
    assert(shouldDropAfterRecheck(0,0,.95f,.2f));
    std::cout << "Version059Tests: 13 guarded recheck conditions PASSED\n";
}
