
#include <bits/stdc++.h>

using namespace std;

    
namespace lift {; 
#ifndef COSH_UF_H
#define COSH_UF_H
; 
float cosh_uf(float x){
    { return cosh(x); }; 
}

#endif
 ; 
void cosh(float * v_initial_param_205_113, float * & v_user_func_207_114, int v_N_0){
    // Allocate memory for output pointers
    v_user_func_207_114 = reinterpret_cast<float *>(malloc((v_N_0 * sizeof(float)))); 
    // For each element processed sequentially
    for (int v_i_112 = 0;(v_i_112 <= (-1 + v_N_0)); (++v_i_112)){
        v_user_func_207_114[v_i_112] = cosh_uf(v_initial_param_205_113[v_i_112]); 
    }
}
}; 