
#include <bits/stdc++.h>

using namespace std;

    
namespace lift {; 
#ifndef ARCCOS_UF_H
#define ARCCOS_UF_H
; 
float arccos_uf(float x){
    { return acos(x); }; 
}

#endif
 ; 
void arccos(float * v_initial_param_132_79, float * & v_user_func_134_80, int v_N_0){
    // Allocate memory for output pointers
    v_user_func_134_80 = reinterpret_cast<float *>(malloc((v_N_0 * sizeof(float)))); 
    // For each element processed sequentially
    for (int v_i_78 = 0;(v_i_78 <= (-1 + v_N_0)); (++v_i_78)){
        v_user_func_134_80[v_i_78] = arccos_uf(v_initial_param_132_79[v_i_78]); 
    }
}
}; 