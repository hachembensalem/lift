
#include <bits/stdc++.h>

using namespace std;

    
namespace lift {; 
#ifndef DIVIDE_UF_H
#define DIVIDE_UF_H
; 
float divide_uf(float x, float y){
    return x / y;; 
}

#endif
 ; 
void true_divide(float * v_initial_param_631_280, float * v_initial_param_632_281, float * & v_user_func_638_283, int v_N_0){
    // Allocate memory for output pointers
    v_user_func_638_283 = reinterpret_cast<float *>(malloc((v_N_0 * sizeof(float)))); 
    // For each element processed sequentially
    for (int v_i_279 = 0;(v_i_279 <= (-1 + v_N_0)); (++v_i_279)){
        v_user_func_638_283[v_i_279] = divide_uf(v_initial_param_631_280[v_i_279], v_initial_param_632_281[v_i_279]); 
    }
}
}; 