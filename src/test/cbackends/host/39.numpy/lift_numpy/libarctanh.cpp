
#include <bits/stdc++.h>

using namespace std;

    
namespace lift {; 
#ifndef ARCTANH_UF_H
#define ARCTANH_UF_H
; 
float arctanh_uf(float x){
    { return atanh(x); }; 
}

#endif
 ; 
void arctanh(float * v_initial_param_249_143, float * & v_user_func_251_144, int v_N_0){
    // Allocate memory for output pointers
    v_user_func_251_144 = reinterpret_cast<float *>(malloc((v_N_0 * sizeof(float)))); 
    // For each element processed sequentially
    for (int v_i_142 = 0;(v_i_142 <= (-1 + v_N_0)); (++v_i_142)){
        v_user_func_251_144[v_i_142] = arctanh_uf(v_initial_param_249_143[v_i_142]); 
    }
}
}; 