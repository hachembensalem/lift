

using namespace std;

#include <math.h>
#include <iostream>

#include "TMRevere.hpp"

    ; 

#ifndef D2R_UF_H
#define D2R_UF_H
; 
double d2r_uf(double x){
    { return x*M_PI/180; }; 
}

#endif
 ; 
void lift_radians_kernel(int argc, vector<TMData *> argv){
    // Pop input, output pointers and sizes
    double * v_initial_param_14056_5294 = reinterpret_cast<double *>(GPEQ_POP());
    double * v_user_func_14060_5358 = reinterpret_cast<double *>(GPEQ_POP());
    int v_N_4617 = GPEQ_POP();
    for (int v_tile_batch_16170 = 0;(v_tile_batch_16170 <= (((v_N_4617)/(8)) / 2)); (++v_tile_batch_16170)){
        int v_virtual_tile_id_16171 = (GPE_TILE_ID() + (v_tile_batch_16170 * 2));
        int v_i_5291 = v_virtual_tile_id_16171;
        if ((v_virtual_tile_id_16171 < ((v_N_4617)/(8)))){
            {
                for (int v_gpe_batch_16172 = 0;(v_gpe_batch_16172 <= 1); (++v_gpe_batch_16172)){
                    ; 
                    int v_i_5292 = GPEQ_POP();
                    if ((v_i_5292 < 4)){
                        // For each element processed sequentially
                        for (int v_i_5293 = 0;(v_i_5293 < 2); v_i_5293 = (v_i_5293 + 1)){
                            v_user_func_14060_5358[(v_i_5293 + (2 * v_i_5292) + (8 * v_i_5291))] = d2r_uf(v_initial_param_14056_5294[(v_i_5293 + (2 * v_i_5292) + (8 * v_i_5291))]); 
                        }
                    }
                    LCPQ_PUSH(1); 
                }
            }
        }
    }
}