
#include <bits/stdc++.h>

using namespace std;

    
namespace lift {; 
#ifndef NEGATIVE_UF_H
#define NEGATIVE_UF_H
; 
float negative_uf(float x){
    return (-1.0f)*x; 
}

#endif
 ; 
void negative(float * v_initial_param_609_256, float * & v_user_func_611_257, int v_N_0){
    // Allocate memory for output pointers
    v_user_func_611_257 = reinterpret_cast<float *>(malloc((v_N_0 * sizeof(float)))); 
    // For each element processed sequentially
    for (int v_i_255 = 0;(v_i_255 <= (-1 + v_N_0)); (++v_i_255)){
        v_user_func_611_257[v_i_255] = negative_uf(v_initial_param_609_256[v_i_255]); 
    }
}
}; 