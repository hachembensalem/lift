
#include <bits/stdc++.h>

using namespace std;

    
namespace lift {; 
#ifndef SUBTRACT_H
#define SUBTRACT_H
; 
float subtract(float l, float r){
    { return l - r; }; 
}

#endif
 ; 
void subtract(float * v_initial_param_664_280, float * v_initial_param_665_281, float * & v_user_func_671_283, int v_N_0){
    // Allocate memory for output pointers
    v_user_func_671_283 = reinterpret_cast<float *>(malloc((v_N_0 * sizeof(float)))); 
    // For each element processed sequentially
    for (int v_i_279 = 0;(v_i_279 <= (-1 + v_N_0)); (++v_i_279)){
        v_user_func_671_283[v_i_279] = subtract(v_initial_param_664_280[v_i_279], v_initial_param_665_281[v_i_279]); 
    }
}
}; 