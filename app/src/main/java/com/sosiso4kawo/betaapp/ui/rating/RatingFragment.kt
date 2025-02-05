package com.sosiso4kawo.betaapp.ui.rating

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.sosiso4kawo.betaapp.R
import com.sosiso4kawo.betaapp.databinding.FragmentRatingBinding

class RatingFragment : Fragment() {

    private var _binding: FragmentRatingBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRatingBinding.inflate(inflater, container, false)
        
        // Set up the header with color and title
        binding.header.apply {
            setBackgroundColor(R.color.header_rating)
            setTitle("Рейтинг")
        }
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}