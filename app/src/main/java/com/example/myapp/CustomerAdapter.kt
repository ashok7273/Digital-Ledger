package com.example.myapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class CustomerAdapter(
    private var customers: List<CustomerEntity>,
    private val onItemClick: (CustomerEntity) -> Unit
) : RecyclerView.Adapter<CustomerAdapter.CustomerViewHolder>() {

    inner class CustomerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tvCustomerName)
        val tvInitial: TextView = itemView.findViewById(R.id.tvCustomerInitial)
        val ivAvatarCircle: ImageView = itemView.findViewById(R.id.ivAvatarCircle)
        val tvBalance: TextView = itemView.findViewById(R.id.tvCustomerBalance)
        val tvBalanceLabel: TextView = itemView.findViewById(R.id.tvBalanceLabel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CustomerViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_customer, parent, false)
        return CustomerViewHolder(view)
    }

    override fun getItemCount() = customers.size

    override fun onBindViewHolder(holder: CustomerViewHolder, position: Int) {
        val customer = customers[position]
        holder.tvName.text = customer.name

        ProfileImageUtils.applyProfileVisual(
            imagePath = customer.profileImagePath,
            displayName = customer.name,
            imageView = holder.ivAvatarCircle,
            initialView = holder.tvInitial
        )

        // Balance display
        val balance = customer.balance
        when {
            balance > 0.0 -> {
                val green = holder.itemView.context.getColor(R.color.color_received)
                holder.tvBalance.text = "₹%.2f".format(balance)
                holder.tvBalance.setTextColor(green)
                holder.tvBalanceLabel.text = "you'll get"
                holder.tvBalanceLabel.setTextColor(green)
            }
            balance < 0.0 -> {
                val red = holder.itemView.context.getColor(R.color.color_given)
                holder.tvBalance.text = "₹%.2f".format(Math.abs(balance))
                holder.tvBalance.setTextColor(red)
                holder.tvBalanceLabel.text = "you'll give"
                holder.tvBalanceLabel.setTextColor(red)
            }
            else -> {
                val gray = holder.itemView.context.getColor(R.color.hint_gray)
                holder.tvBalance.text = "₹0.00"
                holder.tvBalance.setTextColor(gray)
                holder.tvBalanceLabel.text = "settled"
                holder.tvBalanceLabel.setTextColor(gray)
            }
        }

        holder.itemView.setOnClickListener { onItemClick(customer) }
    }

    fun updateList(newList: List<CustomerEntity>) {
        customers = newList
        notifyDataSetChanged()
    }
}